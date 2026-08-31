package com.acme.clm.api;

import com.acme.clm.common.ApiExceptions;
import com.acme.clm.config.CurrentUser;
import com.acme.clm.domain.AutoRejectRule;
import com.acme.clm.domain.Contract;
import com.acme.clm.repo.Repos;
import com.acme.clm.service.AccessService;
import com.acme.clm.service.AuditService;
import com.acme.clm.service.AutoRejectService;
import java.time.Instant;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Approver auto-rejection rules. Each approver manages their own rules: scope selects
 * (contract type / country / entity), natural-language instructions interpreted by AI into
 * executable requirements, live toggle, and a dry-run against a real contract.
 */
@RestController
@RequestMapping("/api/auto-reject")
@PreAuthorize("hasAuthority('PERM_APPROVE')")
public class AutoRejectController {

    private static final String FIRED_ACTION = "FIRED";
    private static final String RULE_AUDIT_TYPE = "AUTO_REJECT_RULE";

    private final AutoRejectService service;
    private final Repos.AutoRejectRules rules;
    private final Repos.Contracts contracts;
    private final Repos.AuditEvents auditEvents;
    private final AccessService access;
    private final AuditService audit;
    private final CurrentUser current;

    public AutoRejectController(AutoRejectService service, Repos.AutoRejectRules rules,
                                Repos.Contracts contracts, Repos.AuditEvents auditEvents,
                                AccessService access, AuditService audit, CurrentUser current) {
        this.service = service;
        this.rules = rules;
        this.contracts = contracts;
        this.auditEvents = auditEvents;
        this.access = access;
        this.audit = audit;
        this.current = current;
    }

    @GetMapping("/rules")
    public List<Map<String, Object>> rules() {
        return service.listFor(current.id(), access.seesEverything(current.id()));
    }

    // -------------------------------------------------- global trigger timing

    public record SettingRequest(String mode, Integer delayHours) {}

    @GetMapping("/settings")
    public Map<String, Object> settings() {
        return settingMap(service.settingFor(current.id()));
    }

    @PutMapping("/settings")
    public Map<String, Object> updateSettings(@RequestBody SettingRequest req) {
        return settingMap(service.updateSetting(current.id(), req.mode(), req.delayHours()));
    }

    private Map<String, Object> settingMap(com.acme.clm.domain.AutoRejectSetting s) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("mode", s.mode.name());
        m.put("delayHours", s.delayHours);
        m.put("updatedAt", s.updatedAt);
        return m;
    }

    public record RuleRequest(String name, Boolean enabled, Map<String, Object> scope,
                              String instructions) {}

    @PostMapping("/rules")
    public Map<String, Object> create(@RequestBody RuleRequest req) {
        UUID me = current.id();
        String name = req.name() == null ? "" : req.name().trim();
        if (name.isEmpty()) throw new ApiExceptions.BadRequestException("Name is required");
        if (name.length() > 200) throw new ApiExceptions.BadRequestException("Name is too long (max 200 characters)");
        String instructions = req.instructions() == null ? "" : req.instructions().trim();
        if (instructions.isEmpty()) throw new ApiExceptions.BadRequestException("Describe the rejection conditions first");
        if (instructions.length() > 4000) throw new ApiExceptions.BadRequestException("Instructions are too long (max 4000 characters)");

        AutoRejectRule r = new AutoRejectRule();
        r.ownerUserId = me;
        r.name = name;
        r.enabled = !Boolean.FALSE.equals(req.enabled());
        r.scope = com.acme.clm.common.Json.write(cleanScope(req.scope()));
        r.instructions = instructions;
        rules.save(r);
        audit.record(RULE_AUDIT_TYPE, r.id.toString(), "CREATED", me, null,
                Map.of("name", name, "scope", service.scopeContext(com.acme.clm.common.Json.readMap(r.scope))));
        return service.toMap(r);
    }

    @PutMapping("/rules/{id}")
    public Map<String, Object> update(@PathVariable UUID id, @RequestBody RuleRequest req) {
        UUID me = current.id();
        AutoRejectRule r = service.owned(id, me);
        boolean scopeChanged = false;
        if (req.name() != null) {
            String name = req.name().trim();
            if (name.isEmpty() || name.length() > 200)
                throw new ApiExceptions.BadRequestException("Name must be 1-200 characters");
            r.name = name;
        }
        if (req.enabled() != null) r.enabled = req.enabled();
        if (req.scope() != null) {
            r.scope = com.acme.clm.common.Json.write(cleanScope(req.scope()));
            scopeChanged = true;
        }
        if (req.instructions() != null) {
            String instructions = req.instructions().trim();
            if (instructions.isEmpty()) throw new ApiExceptions.BadRequestException("Instructions cannot be empty");
            if (instructions.length() > 4000)
                throw new ApiExceptions.BadRequestException("Instructions are too long (max 4000 characters)");
            if (!instructions.equals(r.instructions)) {
                // the words changed — the stored interpretation no longer reflects them
                r.structured = null;
                r.interpretationModel = null;
                r.interpretedAt = null;
                r.instructions = instructions;
            }
        }
        r.updatedAt = Instant.now();
        rules.save(r);
        if (scopeChanged) {
            audit.record(RULE_AUDIT_TYPE, r.id.toString(), "SCOPE_CHANGED", me, null,
                    Map.of("scope", service.scopeContext(com.acme.clm.common.Json.readMap(r.scope))));
        }
        return service.toMap(r);
    }

    @DeleteMapping("/rules/{id}")
    public Map<String, Object> delete(@PathVariable UUID id) {
        UUID me = current.id();
        AutoRejectRule r = service.owned(id, me);
        rules.delete(r);
        audit.record(RULE_AUDIT_TYPE, r.id.toString(), "DELETED", me, null, Map.of("name", r.name));
        return Map.of("deleted", true);
    }

    /** Re-runs AI structuring on the rule's instructions. Deliberately not @Transactional: the
     *  LLM call (~seconds) must not pin a pooled DB connection. */
    @PostMapping("/rules/{id}/interpret")
    public Map<String, Object> interpret(@PathVariable UUID id) {
        AutoRejectRule r = service.owned(id, current.id());
        r = service.interpret(r);
        audit.record(RULE_AUDIT_TYPE, r.id.toString(), "INTERPRETED", current.id(), null,
                Map.of("summary", String.valueOf(com.acme.clm.common.Json.readMap(r.structured).get("summary"))));
        return service.toMap(r);
    }

    public record TestRequest(UUID contractId) {}

    /** Dry-run the rule against one contract without any side effects. */
    @PostMapping("/rules/{id}/test")
    public Map<String, Object> test(@PathVariable UUID id, @RequestBody TestRequest req) {
        AutoRejectRule r = service.owned(id, current.id());
        if (req.contractId() == null) throw new ApiExceptions.BadRequestException("contractId is required");
        Contract c = contracts.findById(req.contractId())
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Contract not found"));
        if (!access.canView(current.id(), c))
            throw new ApiExceptions.NotFoundException("Contract not found");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("contractId", c.id);
        out.put("contractNumber", c.contractNumber);
        out.put("contractTitle", c.title);
        boolean scopeMatched = service.scopeMatches(r, c);
        boolean executable = service.isExecutable(r);
        var eval = service.evaluate(r, c);
        out.put("scopeMatched", scopeMatched);
        out.put("executable", executable);
        out.put("satisfied", eval.satisfied());
        out.put("met", eval.met());
        out.put("unmet", eval.unmet());
        out.put("summary", eval.summary());
        out.put("wouldReject", r.enabled && executable && scopeMatched && !eval.satisfied());
        return out;
    }

    /** Refdata for the scope editor: entities (with country) and the distinct country list. */
    @GetMapping("/refdata")
    public Map<String, Object> refdata() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("entities", service.entityCountryOptions());
        m.put("countries", service.knownCountries());
        return m;
    }

    /** Recent fires of the current user's (or everyone's, for admins) rules. */
    @GetMapping("/events")
    public List<Map<String, Object>> events() {
        Map<String, String> mine = new LinkedHashMap<>();
        for (AutoRejectRule r : access.seesEverything(current.id())
                ? rules.findAll() : rules.findByOwnerUserIdOrderByCreatedAtDesc(current.id())) {
            mine.put(r.id.toString(), r.name);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (var e : auditEvents.findTop200ByEntityTypeAndActionOrderByOccurredAtDesc(RULE_AUDIT_TYPE, FIRED_ACTION)) {
            if (!mine.containsKey(e.entityId)) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("occurredAt", e.occurredAt);
            m.put("ruleId", UUID.fromString(e.entityId));
            m.put("ruleName", mine.get(e.entityId));
            m.put("payload", e.afterState == null ? Map.of() : com.acme.clm.common.Json.readMap(e.afterState));
            out.add(m);
        }
        return out;
    }

    private String ruleName(String ruleId) {
        try {
            return rules.findById(UUID.fromString(ruleId)).map(r -> r.name).orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Map<String, Object> cleanScope(Map<String, Object> scope) {
        Map<String, Object> clean = new LinkedHashMap<>();
        clean.put("contractTypes", scope == null ? List.of()
                : AutoRejectService.strList(scope.get("contractTypes")));
        clean.put("countries", scope == null ? List.of()
                : AutoRejectService.strList(scope.get("countries")));
        Object ids = scope == null ? null : scope.get("entityIds");
        List<String> entityIds = new ArrayList<>();
        if (AutoRejectService.strList(ids) != null) {
            for (String s : AutoRejectService.strList(ids)) {
                try { UUID.fromString(s); entityIds.add(s); } catch (IllegalArgumentException ignored) { }
            }
        }
        clean.put("entityIds", entityIds);
        return clean;
    }
}