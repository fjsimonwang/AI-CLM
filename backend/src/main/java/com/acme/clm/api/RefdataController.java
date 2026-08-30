package com.acme.clm.api;

import com.acme.clm.common.Json;
import com.acme.clm.config.Permissions;
import com.acme.clm.repo.Repos;
import com.acme.clm.service.FieldCatalog;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/** Reference data used to populate dropdowns and admin views. */
@RestController
@RequestMapping("/api/refdata")
public class RefdataController {

    private final Repos.LegalEntities entities;
    private final Repos.LegalTeams teams;
    private final Repos.Users users;
    private final Repos.ContractTypes types;
    private final Repos.Parties parties;
    private final FieldCatalog fieldCatalog;

    public RefdataController(Repos.LegalEntities entities, Repos.LegalTeams teams, Repos.Users users,
                             Repos.ContractTypes types, Repos.Parties parties, FieldCatalog fieldCatalog) {
        this.entities = entities;
        this.teams = teams;
        this.users = users;
        this.types = types;
        this.parties = parties;
        this.fieldCatalog = fieldCatalog;
    }

    @GetMapping("/roles")
    public Map<String, Object> roles() {
        return Map.of("roles", Permissions.knownRoles(), "permissions", Permissions.allPermissions());
    }

    @GetMapping("/contract-types/{code}/fields")
    public List<FieldCatalog.Field> typeFields(@PathVariable String code) {
        return fieldCatalog.forType(code);
    }

    @GetMapping("/entities")
    public List<Map<String, Object>> entities() {
        return entities.findAll().stream().map(e -> Map.<String, Object>of(
                "id", e.id, "shortName", e.shortName, "legalName", e.legalName,
                "countryCode", e.countryCode, "governingLaw", e.defaultGoverningLaw == null ? "" : e.defaultGoverningLaw,
                "language", e.defaultLanguage, "region", e.dataResidencyRegion)).toList();
    }

    @GetMapping("/teams")
    public List<Map<String, Object>> teams() {
        return teams.findAll().stream().map(t -> Map.<String, Object>of(
                "id", t.id, "name", t.name, "region", t.region == null ? "" : t.region,
                "slaHours", t.defaultQueueSlaHours)).toList();
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users() {
        return users.findAll().stream().map(u -> Map.<String, Object>of(
                "id", u.id, "email", u.email, "displayName", u.displayName,
                "department", u.department == null ? "" : u.department, "roles", u.roles)).toList();
    }

    @GetMapping("/contract-types")
    public List<Map<String, Object>> contractTypes(@RequestParam(defaultValue = "false") boolean includeInactive) {
        var list = includeInactive ? types.findAll() : types.findByIsActiveTrue();
        return list.stream().sorted((a, b) -> a.displayName.compareTo(b.displayName)).map(t -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("code", t.code);
            m.put("displayName", t.displayName);
            m.put("category", t.category);
            m.put("icon", t.icon);
            m.put("requiresLegalReview", t.requiresLegalReviewDefault);
            m.put("baseRisk", t.baseRisk);
            m.put("autoIssueAllowed", t.autoIssueAllowed);
            m.put("retentionYears", t.retentionYears);
            m.put("isActive", t.isActive);
            m.put("defaultTemplateId", t.defaultTemplateId);
            m.put("defaultWorkflowId", t.defaultWorkflowId);
            m.put("fieldSchema", Json.read(t.fieldSchema));
            m.put("uiGroups", Json.read(t.uiGroups));
            return m;
        }).toList();
    }

    @GetMapping("/parties")
    public List<Map<String, Object>> parties(@RequestParam(required = false) String q) {
        var stream = (q == null || q.isBlank())
                ? parties.findAll().stream()
                : parties.findTop10ByLegalNameContainingIgnoreCaseOrTradingNameContainingIgnoreCase(q, q).stream();
        return stream.map(p -> Map.<String, Object>of(
                "id", p.id, "legalName", p.legalName, "tradingName", p.tradingName == null ? "" : p.tradingName,
                "country", p.countryCode == null ? "" : p.countryCode, "type", p.partyType,
                "sanctionsStatus", p.sanctionsCheckStatus, "industry", p.industry == null ? "" : p.industry)).toList();
    }
}
