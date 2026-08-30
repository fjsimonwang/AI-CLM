package com.acme.clm.api;

import com.acme.clm.common.ApiExceptions;
import com.acme.clm.common.Json;
import com.acme.clm.config.CurrentUser;
import com.acme.clm.domain.*;
import com.acme.clm.repo.Repos;
import com.acme.clm.service.AccessService;
import com.acme.clm.service.AuditService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.web.bind.annotation.*;

/**
 * Self-service access: users see their granted scope, apply for more, and (as scope owners)
 * approve applications. Which approver can decide a request is resolved from the approver-scope
 * matrix (plan §12 attribute-based scoping).
 */
@RestController
@RequestMapping("/api/access")
public class AccessController {

    private final Repos.AccessDimensions dimensions;
    private final Repos.AccessGrants grants;
    private final Repos.AccessRequests requests;
    private final Repos.Users users;
    private final AccessService access;
    private final AuditService audit;
    private final CurrentUser current;

    public AccessController(Repos.AccessDimensions dimensions, Repos.AccessGrants grants, Repos.AccessRequests requests,
                            Repos.Users users, AccessService access, AuditService audit, CurrentUser current) {
        this.dimensions = dimensions;
        this.grants = grants;
        this.requests = requests;
        this.users = users;
        this.access = access;
        this.audit = audit;
        this.current = current;
    }

    @GetMapping("/dimensions")
    public List<Map<String, Object>> dims() {
        return access.activeDimensions().stream().map(d -> Map.<String, Object>of(
                "code", d.code, "name", d.name, "description", d.description == null ? "" : d.description,
                "derivation", d.derivation, "values", Json.read(d.valueOptions))).toList();
    }

    @GetMapping("/my-grants")
    public List<Map<String, Object>> myGrants() {
        return access.activeGrants(current.id()).stream().map(access::describeGrant).toList();
    }

    @GetMapping("/my-requests")
    public List<Map<String, Object>> myRequests() {
        return requests.findByUserIdOrderByCreatedAtDesc(current.id()).stream().map(this::requestView).toList();
    }

    public record PreviewRequest(Map<String, Object> constraints) {}

    /** Who could approve a request for this scope? */
    @PostMapping("/preview")
    public Map<String, Object> preview(@RequestBody PreviewRequest req) {
        var c = access.constraintsOf(Json.write(req.constraints() == null ? Map.of() : req.constraints()));
        var approvers = access.eligibleApprovers(c);
        return Map.of(
                "scope", access.humanScope(c),
                "approvers", approvers.stream().map(a -> Map.of(
                        "userId", a.userId(), "name", a.displayName(),
                        "scope", access.humanScope(a.scope()))).toList(),
                "approvable", !approvers.isEmpty());
    }

    public record ApplyRequest(Map<String, Object> constraints, String justification, Integer expiresDays) {}

    @PostMapping("/requests")
    public Map<String, Object> apply(@RequestBody ApplyRequest req) {
        Map<String, Object> raw = req.constraints() == null ? Map.of() : req.constraints();
        if (raw.isEmpty()) throw new ApiExceptions.BadRequestException("Choose at least one dimension to scope the request.");
        var c = access.constraintsOf(Json.write(raw));
        if (access.eligibleApprovers(c).isEmpty()) {
            throw new ApiExceptions.BadRequestException(
                    "No approver owns a scope that covers " + access.humanScope(c)
                            + ". Ask an administrator to define an approver scope for it.");
        }
        AccessRequest r = new AccessRequest();
        r.userId = current.id();
        r.constraints = Json.write(raw);
        r.justification = req.justification();
        r.expiresDays = req.expiresDays();
        requests.save(r);
        audit.record("ACCESS_REQUEST", r.id.toString(), "SUBMITTED", current.id(), null,
                Map.of("scope", access.humanScope(c)));
        return requestView(r);
    }

    @PostMapping("/requests/{id}/withdraw")
    public Map<String, Object> withdraw(@PathVariable UUID id) {
        AccessRequest r = requests.findById(id).orElseThrow(() -> new ApiExceptions.NotFoundException("Not found"));
        if (!r.userId.equals(current.id())) throw new ApiExceptions.ForbiddenException("Not your request");
        r.status = "WITHDRAWN";
        requests.save(r);
        return requestView(r);
    }

    /** Requests the current user is entitled to decide. */
    @GetMapping("/requests/pending")
    public List<Map<String, Object>> pending() {
        UUID me = current.id();
        return requests.findByStatusOrderByCreatedAtDesc("PENDING").stream()
                .filter(r -> !r.userId.equals(me))
                .filter(r -> access.canApprove(me, access.constraintsOf(r.constraints)))
                .map(this::requestView).toList();
    }

    public record Decision(boolean approve, String note) {}

    @PostMapping("/requests/{id}/decide")
    public Map<String, Object> decide(@PathVariable UUID id, @RequestBody Decision d) {
        AccessRequest r = requests.findById(id).orElseThrow(() -> new ApiExceptions.NotFoundException("Not found"));
        if (!"PENDING".equals(r.status)) throw new ApiExceptions.BadRequestException("Already decided");
        UUID me = current.id();
        var c = access.constraintsOf(r.constraints);
        if (!access.canApprove(me, c)) {
            throw new ApiExceptions.ForbiddenException("Your approver scope does not cover " + access.humanScope(c));
        }
        r.status = d.approve() ? "APPROVED" : "REJECTED";
        r.decidedBy = me;
        r.decidedAt = Instant.now();
        r.decisionNote = d.note();
        requests.save(r);

        if (d.approve()) {
            AccessGrant g = new AccessGrant();
            g.userId = r.userId;
            g.name = "Approved: " + access.humanScope(c);
            g.constraints = r.constraints;
            g.source = "REQUEST";
            g.grantedBy = me;
            g.requestId = r.id;
            if (r.expiresDays != null && r.expiresDays > 0) g.expiresAt = Instant.now().plus(r.expiresDays, ChronoUnit.DAYS);
            grants.save(g);
        }
        audit.record("ACCESS_REQUEST", r.id.toString(), d.approve() ? "APPROVED" : "REJECTED", me, null,
                Map.of("scope", access.humanScope(c), "note", d.note() == null ? "" : d.note()));
        return requestView(r);
    }

    private Map<String, Object> requestView(AccessRequest r) {
        var c = access.constraintsOf(r.constraints);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.id);
        m.put("user", users.findById(r.userId).map(u -> u.displayName).orElse("?"));
        m.put("userId", r.userId);
        m.put("constraints", c);
        m.put("scope", access.humanScope(c));
        m.put("justification", r.justification);
        m.put("status", r.status);
        m.put("createdAt", r.createdAt);
        m.put("decidedBy", r.decidedBy == null ? null : users.findById(r.decidedBy).map(u -> u.displayName).orElse("?"));
        m.put("decidedAt", r.decidedAt);
        m.put("decisionNote", r.decisionNote);
        m.put("eligibleApprovers", access.eligibleApprovers(c).stream().map(AccessService.ApproverOption::displayName).toList());
        return m;
    }
}
