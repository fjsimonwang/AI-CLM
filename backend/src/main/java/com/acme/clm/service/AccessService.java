package com.acme.clm.service;

import com.acme.clm.common.Json;
import com.acme.clm.domain.*;
import com.acme.clm.repo.Repos;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Attribute-based access control (plan §12: "RBAC plus attribute-based scoping").
 *
 * A user may view a contract if ANY of:
 *  - they hold a broad role (ADMIN / GENERAL_COUNSEL);
 *  - they are directly involved (owner, creator, assigned lawyer, participant, workflow assignee);
 *  - they hold an active {@link AccessGrant} whose dimension constraints all match the contract.
 *
 * Access is applied for or granted directly; an application is approvable only by an
 * {@link ApproverScope} that COVERS the requested scope on every constrained dimension.
 */
@Service
public class AccessService {

    private final Repos.Contracts contracts;
    private final Repos.Users users;
    private final Repos.LegalEntities entities;
    private final Repos.ContractParticipants participants;
    private final Repos.WorkflowInstances workflowInstances;
    private final Repos.WorkflowTasks workflowTasks;
    private final Repos.AccessDimensions dimensions;
    private final Repos.AccessGrants grants;
    private final Repos.ApproverScopes approverScopes;

    public AccessService(Repos.Contracts contracts, Repos.Users users, Repos.LegalEntities entities,
                         Repos.ContractParticipants participants, Repos.WorkflowInstances workflowInstances,
                         Repos.WorkflowTasks workflowTasks, Repos.AccessDimensions dimensions,
                         Repos.AccessGrants grants, Repos.ApproverScopes approverScopes) {
        this.contracts = contracts;
        this.users = users;
        this.entities = entities;
        this.participants = participants;
        this.workflowInstances = workflowInstances;
        this.workflowTasks = workflowTasks;
        this.dimensions = dimensions;
        this.grants = grants;
        this.approverScopes = approverScopes;
    }

    // ----------------------------------------------------- role bypass

    public boolean seesEverything(UUID userId) {
        return users.findById(userId).map(u -> u.roles != null
                && (u.roles.contains("ADMIN") || u.roles.contains("GENERAL_COUNSEL"))).orElse(false);
    }

    // ----------------------------------------------------- dimension derivation

    public List<AccessDimension> activeDimensions() {
        return dimensions.findByIsActiveTrueOrderBySortOrderAsc();
    }

    /** Compute this contract's value on every active dimension. */
    public Map<String, String> dimensionValues(Contract c) {
        Map<String, String> out = new LinkedHashMap<>();
        LegalEntity entity = entities.findById(c.contractingEntityId).orElse(null);
        AppUser owner = c.ownerUserId == null ? null : users.findById(c.ownerUserId).orElse(null);
        Map<String, Object> attrs = Json.readMap(c.typeAttributes);
        for (AccessDimension d : activeDimensions()) {
            out.put(d.code, derive(d.derivation, c, entity, owner, attrs));
        }
        return out;
    }

    private String derive(String derivation, Contract c, LegalEntity entity, AppUser owner, Map<String, Object> attrs) {
        if (derivation == null) return null;
        if (derivation.equals("owner_department")) return owner == null ? null : owner.department;
        String[] p = derivation.split(":", 2);
        if (p.length != 2) return null;
        return switch (p[0]) {
            case "field" -> contractField(c, p[1]);
            case "entity" -> entity == null ? null : entityField(entity, p[1]);
            case "type_attribute" -> attrs.get(p[1]) == null ? null : String.valueOf(attrs.get(p[1]));
            default -> null;
        };
    }

    private String contractField(Contract c, String f) {
        return switch (f) {
            case "contract_type_code" -> c.contractTypeCode;
            case "confidentiality_level" -> c.confidentialityLevel;
            case "status" -> c.status;
            case "governing_law_code" -> c.governingLawCode;
            case "currency" -> c.currency;
            case "source" -> c.source;
            default -> null;
        };
    }

    private String entityField(LegalEntity e, String f) {
        return switch (f) {
            case "data_residency_region" -> e.dataResidencyRegion;
            case "short_name" -> e.shortName;
            case "country_code" -> e.countryCode;
            case "default_governing_law" -> e.defaultGoverningLaw;
            default -> null;
        };
    }

    // ----------------------------------------------------- visibility

    @Transactional(readOnly = true)
    public boolean canView(UUID userId, Contract c) {
        if (c == null) return false;
        if (seesEverything(userId)) return true;
        if (involvedIn(userId, c)) return true;
        Map<String, String> dv = dimensionValues(c);
        return activeGrants(userId).stream().anyMatch(g -> grantMatches(constraintsOf(g.constraints), dv));
    }

    /** Filter a list of contracts to those the user may see. */
    @Transactional(readOnly = true)
    public List<Contract> filterVisible(UUID userId, List<Contract> all) {
        if (seesEverything(userId)) return all;
        Set<UUID> involved = involvedContractIds(userId);
        List<Map<String, List<String>>> myGrants = activeGrants(userId).stream()
                .map(g -> constraintsOf(g.constraints)).toList();
        return all.stream().filter(c -> {
            if (involved.contains(c.id)) return true;
            if (myGrants.isEmpty()) return false;
            Map<String, String> dv = dimensionValues(c);
            return myGrants.stream().anyMatch(gc -> grantMatches(gc, dv));
        }).toList();
    }

    private boolean involvedIn(UUID userId, Contract c) {
        if (userId.equals(c.ownerUserId) || userId.equals(c.createdBy) || userId.equals(c.assignedLawyerId)) return true;
        if (participants.findById(new ContractParticipant.Key(c.id, userId)).isPresent()) return true;
        for (WorkflowInstance wi : workflowInstances.findByContractId(c.id)) {
            if (workflowTasks.findByWorkflowInstanceId(wi.id).stream().anyMatch(t -> userId.equals(t.assignedUserId)))
                return true;
        }
        return false;
    }

    private Set<UUID> involvedContractIds(UUID userId) {
        Set<UUID> ids = new HashSet<>();
        contracts.findAll().forEach(c -> {
            if (userId.equals(c.ownerUserId) || userId.equals(c.createdBy) || userId.equals(c.assignedLawyerId))
                ids.add(c.id);
        });
        participants.findByUserId(userId).forEach(p -> ids.add(p.contractId));
        workflowTasks.findByAssignedUserIdAndStatus(userId, "OPEN")
                .forEach(t -> workflowInstances.findById(t.workflowInstanceId).ifPresent(wi -> ids.add(wi.contractId)));
        // also closed tasks the user acted on
        for (WorkflowTask t : workflowTasks.findAll()) {
            if (userId.equals(t.assignedUserId)) {
                workflowInstances.findById(t.workflowInstanceId).ifPresent(wi -> ids.add(wi.contractId));
            }
        }
        return ids;
    }

    // ----------------------------------------------------- grants / matching

    public List<AccessGrant> activeGrants(UUID userId) {
        Instant now = Instant.now();
        return grants.findByUserIdAndStatus(userId, "ACTIVE").stream()
                .filter(g -> g.expiresAt == null || g.expiresAt.isAfter(now))
                .toList();
    }

    @SuppressWarnings("unchecked")
    public Map<String, List<String>> constraintsOf(String json) {
        Map<String, Object> raw = Json.readMap(json);
        Map<String, List<String>> out = new LinkedHashMap<>();
        raw.forEach((k, v) -> {
            List<String> vals = new ArrayList<>();
            if (v instanceof List<?> l) l.forEach(x -> vals.add(String.valueOf(x)));
            else if (v != null) vals.add(String.valueOf(v));
            out.put(k, vals);
        });
        return out;
    }

    /** A grant matches a contract if, for every dimension it constrains, the contract's value is allowed. */
    public boolean grantMatches(Map<String, List<String>> constraints, Map<String, String> dimValues) {
        for (Map.Entry<String, List<String>> e : constraints.entrySet()) {
            List<String> allowed = e.getValue();
            if (allowed == null || allowed.isEmpty() || allowed.contains("*")) continue;
            String actual = dimValues.get(e.getKey());
            if (actual == null || !allowed.contains(actual)) return false;
        }
        return true;
    }

    // ----------------------------------------------------- approver matrix

    /** An approver scope covers a request if, for every dimension the request constrains,
     *  the scope is unconstrained ("*"/absent) or a superset of the requested values. */
    public boolean scopeCovers(Map<String, List<String>> scope, Map<String, List<String>> request) {
        for (Map.Entry<String, List<String>> e : request.entrySet()) {
            List<String> requested = e.getValue();
            if (requested == null || requested.isEmpty() || requested.contains("*")) {
                // request asks for "any" on this dimension → only a "*" scope covers it
                List<String> s = scope.get(e.getKey());
                if (s != null && !s.isEmpty() && !s.contains("*")) return false;
                continue;
            }
            List<String> allowed = scope.get(e.getKey());
            if (allowed == null || allowed.isEmpty() || allowed.contains("*")) continue; // scope unconstrained here
            if (!allowed.containsAll(requested)) return false;
        }
        return true;
    }

    public record ApproverOption(UUID userId, String displayName, String scopeName, Map<String, List<String>> scope) {}

    public List<ApproverOption> eligibleApprovers(Map<String, List<String>> requestConstraints) {
        List<ApproverOption> out = new ArrayList<>();
        for (ApproverScope s : approverScopes.findByIsActiveTrue()) {
            Map<String, List<String>> sc = constraintsOf(s.constraints);
            if (scopeCovers(sc, requestConstraints)) {
                users.findById(s.userId).ifPresent(u ->
                        out.add(new ApproverOption(u.id, u.displayName, s.name == null ? "scope" : s.name, sc)));
            }
        }
        // de-duplicate by user, keeping the tightest-looking scope name
        Map<UUID, ApproverOption> byUser = new LinkedHashMap<>();
        out.forEach(o -> byUser.putIfAbsent(o.userId(), o));
        return new ArrayList<>(byUser.values());
    }

    public boolean canApprove(UUID approverUserId, Map<String, List<String>> requestConstraints) {
        return approverScopes.findByUserIdAndIsActiveTrue(approverUserId).stream()
                .anyMatch(s -> scopeCovers(constraintsOf(s.constraints), requestConstraints));
    }

    // ----------------------------------------------------- summary for the UI

    public Map<String, Object> describeGrant(AccessGrant g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", g.id);
        m.put("name", g.name);
        m.put("constraints", constraintsOf(g.constraints));
        m.put("status", g.status);
        m.put("source", g.source);
        m.put("grantedAt", g.grantedAt);
        m.put("expiresAt", g.expiresAt);
        return m;
    }

    public String humanScope(Map<String, List<String>> c) {
        if (c.isEmpty()) return "everything";
        return c.entrySet().stream()
                .map(e -> e.getKey() + ": " + (e.getValue().isEmpty() || e.getValue().contains("*") ? "any" : String.join("/", e.getValue())))
                .collect(Collectors.joining(" · "));
    }
}
