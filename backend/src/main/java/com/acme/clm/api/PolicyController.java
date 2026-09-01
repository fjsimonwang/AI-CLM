package com.acme.clm.api;

import com.acme.clm.common.ApiExceptions;
import com.acme.clm.config.CurrentUser;
import com.acme.clm.domain.PolicyDocument;
import com.acme.clm.repo.Repos;
import com.acme.clm.service.AuditService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin CRUD for CLM policy & procedure documents that ground the help assistant. Each document's
 * audience is set with access dimensions (roles / countries / regions — comma-separated, blank =
 * all) and a confidential flag. The help chat reads them via
 * {@link com.acme.clm.service.PolicyService}.
 */
@RestController
@RequestMapping("/api/admin/policies")
@PreAuthorize("hasAuthority('PERM_MANAGE_MASTERDATA')")
public class PolicyController {

    private final Repos.PolicyDocuments policies;
    private final AuditService audit;
    private final CurrentUser current;

    public PolicyController(Repos.PolicyDocuments policies, AuditService audit, CurrentUser current) {
        this.policies = policies;
        this.audit = audit;
        this.current = current;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return policies.findAll().stream()
                .sorted((a, b) -> a.title.compareToIgnoreCase(b.title))
                .map(p -> row(p, false))
                .toList();
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable UUID id) {
        return row(policies.findById(id).orElseThrow(() -> new ApiExceptions.NotFoundException("Policy not found")), true);
    }

    @PostMapping
    public Map<String, Object> save(@RequestBody Map<String, Object> b) {
        PolicyDocument p = uid(b.get("id")) != null
                ? policies.findById(uid(b.get("id"))).orElseThrow(() -> new ApiExceptions.NotFoundException("Policy not found"))
                : new PolicyDocument();
        if (b.containsKey("title")) p.title = str(b.get("title"));
        if (b.containsKey("bodyHtml")) p.bodyHtml = str(b.get("bodyHtml"));
        if (b.containsKey("roles")) p.roles = csv(b.get("roles"));
        if (b.containsKey("countries")) p.countries = csv(b.get("countries"));
        if (b.containsKey("regions")) p.regions = csv(b.get("regions"));
        if (b.containsKey("confidential")) p.confidential = truthy(b.get("confidential"));
        if (b.containsKey("isActive")) p.isActive = truthy(b.get("isActive"));
        if (p.title == null || p.title.isBlank()) throw new ApiExceptions.BadRequestException("Title is required");
        if (p.bodyHtml == null || p.bodyHtml.isBlank())
            throw new ApiExceptions.BadRequestException("Upload a document or paste its text so the assistant has something to learn from.");
        if (p.uploadedBy == null) p.uploadedBy = current.id();
        policies.save(p);
        audit.record("POLICY_DOCUMENT", p.id.toString(), "SAVED", current.id(), null,
                Map.of("title", p.title, "confidential", p.confidential));
        return row(p, true);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        policies.findById(id).ifPresent(p -> {
            policies.delete(p);
            audit.record("POLICY_DOCUMENT", id.toString(), "DELETED", current.id(), null, Map.of("title", p.title));
        });
    }

    private static Map<String, Object> row(PolicyDocument p, boolean full) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id);
        m.put("title", p.title);
        m.put("roles", p.roles);
        m.put("countries", p.countries);
        m.put("regions", p.regions);
        m.put("audience", audience(p));
        m.put("confidential", p.confidential);
        m.put("isActive", p.isActive);
        m.put("createdAt", p.createdAt);
        if (full) m.put("bodyHtml", p.bodyHtml);
        return m;
    }

    private static String audience(PolicyDocument p) {
        StringBuilder sb = new StringBuilder();
        sb.append(p.roles.isBlank() ? "All roles" : p.roles);
        if (!p.countries.isBlank()) sb.append(" · ").append(p.countries);
        if (!p.regions.isBlank()) sb.append(" · ").append(p.regions);
        if (p.confidential) sb.append(" · confidential");
        return sb.toString();
    }

    private static String str(Object v) { return v == null ? null : String.valueOf(v); }
    private static boolean truthy(Object v) { return v != null && String.valueOf(v).equalsIgnoreCase("true"); }
    private static UUID uid(Object v) {
        try { return v == null || String.valueOf(v).isBlank() ? null : UUID.fromString(String.valueOf(v)); }
        catch (Exception e) { return null; }
    }

    /** Accepts a List or a comma-separated string; stores a normalised comma-separated string. */
    @SuppressWarnings("unchecked")
    private static String csv(Object v) {
        if (v == null) return "";
        List<String> parts;
        if (v instanceof List<?> l) parts = l.stream().map(String::valueOf).toList();
        else parts = List.of(String.valueOf(v).split("[,;]"));
        return parts.stream().map(String::trim).filter(s -> !s.isEmpty())
                .map(String::toUpperCase).distinct().reduce((a, b) -> a + "," + b).orElse("");
    }
}
