package com.acme.clm.api;

import com.acme.clm.common.ApiExceptions;
import com.acme.clm.config.CurrentUser;
import com.acme.clm.domain.Obligation;
import com.acme.clm.repo.Repos;
import com.acme.clm.service.AccessService;
import com.acme.clm.service.AuditService;
import java.time.LocalDate;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/obligations")
public class ObligationController {

    private final Repos.Obligations obligations;
    private final Repos.Contracts contracts;
    private final Repos.Users users;
    private final AuditService audit;
    private final AccessService access;
    private final CurrentUser current;

    public ObligationController(Repos.Obligations obligations, Repos.Contracts contracts, Repos.Users users,
                                AuditService audit, AccessService access, CurrentUser current) {
        this.obligations = obligations;
        this.contracts = contracts;
        this.users = users;
        this.audit = audit;
        this.access = access;
        this.current = current;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) Integer dueWithinDays) {
        LocalDate horizon = dueWithinDays == null ? null : LocalDate.now().plusDays(dueWithinDays);
        return obligations.findAll().stream()
                .filter(o -> status == null || status.equalsIgnoreCase(o.status))
                .filter(o -> horizon == null || (o.dueDate != null && !o.dueDate.isAfter(horizon)))
                .sorted(Comparator.comparing(o -> o.dueDate == null ? LocalDate.MAX : o.dueDate))
                .map(this::map).toList();
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        LocalDate now = LocalDate.now();
        Set<UUID> visible = access.filterVisible(current.id(), contracts.findAll()).stream()
                .map(c -> c.id).collect(java.util.stream.Collectors.toSet());
        List<Obligation> all = obligations.findAll().stream()
                .filter(o -> visible.contains(o.contractId)).toList();
        long overdue = all.stream().filter(o -> !"CLOSED".equals(o.status) && o.dueDate != null && o.dueDate.isBefore(now)).count();
        long next30 = all.stream().filter(o -> o.dueDate != null && !o.dueDate.isBefore(now) && o.dueDate.isBefore(now.plusDays(30))).count();
        long next90 = all.stream().filter(o -> o.dueDate != null && !o.dueDate.isBefore(now) && o.dueDate.isBefore(now.plusDays(90))).count();
        long unverified = all.stream().filter(o -> o.verifiedBy == null
                && o.extractionConfidence != null && o.extractionConfidence.doubleValue() < 0.75).count();
        return Map.of("overdue", overdue, "dueNext30", next30, "dueNext90", next90,
                "unverifiedLowConfidence", unverified, "total", all.size());
    }

    public record CreateRequest(UUID contractId, String obligationType, String description,
                                LocalDate dueDate, String owningDepartment, Integer alertLeadDays) {}

    @PostMapping
    public Map<String, Object> create(@RequestBody CreateRequest req) {
        contracts.findById(req.contractId()).orElseThrow(() -> new ApiExceptions.BadRequestException("Unknown contract"));
        Obligation o = new Obligation();
        o.contractId = req.contractId();
        o.obligationType = req.obligationType() == null ? "DELIVERABLE" : req.obligationType();
        o.description = req.description();
        o.dueDate = req.dueDate();
        o.owningDepartment = req.owningDepartment();
        o.ownerUserId = current.id();
        o.verifiedBy = current.id();
        o.alertLeadDays = req.alertLeadDays() == null ? 30 : req.alertLeadDays();
        obligations.save(o);
        audit.record("OBLIGATION", o.id.toString(), "CREATED", current.id(), null, Map.of("description", o.description));
        return map(o);
    }

    @PostMapping("/{id}/status")
    public Map<String, Object> setStatus(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        Obligation o = obligations.findById(id).orElseThrow(() -> new ApiExceptions.NotFoundException("Obligation not found"));
        String before = o.status;
        o.status = body.getOrDefault("status", o.status);
        obligations.save(o);
        audit.record("OBLIGATION", id.toString(), "STATUS_CHANGED", current.id(),
                Map.of("status", before), Map.of("status", o.status));
        return map(o);
    }

    @PostMapping("/{id}/verify")
    public Map<String, Object> verify(@PathVariable UUID id) {
        Obligation o = obligations.findById(id).orElseThrow(() -> new ApiExceptions.NotFoundException("Obligation not found"));
        o.verifiedBy = current.id();
        obligations.save(o);
        audit.record("OBLIGATION", id.toString(), "VERIFIED", current.id(), null, null);
        return map(o);
    }

    private Map<String, Object> map(Obligation o) {
        var c = contracts.findById(o.contractId).orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.id);
        m.put("contractId", o.contractId);
        m.put("contractNumber", c == null ? null : c.contractNumber);
        m.put("contractTitle", c == null ? null : c.title);
        m.put("type", o.obligationType);
        m.put("description", o.description);
        m.put("dueDate", o.dueDate);
        m.put("status", o.status);
        m.put("owner", o.ownerUserId == null ? null : users.findById(o.ownerUserId).map(u -> u.displayName).orElse(null));
        m.put("owningDepartment", o.owningDepartment);
        m.put("extractionConfidence", o.extractionConfidence);
        m.put("verified", o.verifiedBy != null);
        m.put("alertLeadDays", o.alertLeadDays);
        boolean overdue = o.dueDate != null && o.dueDate.isBefore(LocalDate.now()) && !"CLOSED".equals(o.status);
        m.put("overdue", overdue);
        return m;
    }
}
