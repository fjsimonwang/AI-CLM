package com.acme.clm.api;

import com.acme.clm.common.ApiExceptions;
import com.acme.clm.domain.ClauseVariant;
import com.acme.clm.repo.Repos;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clauses")
public class ClauseController {

    private final Repos.ClauseConcepts concepts;
    private final Repos.ClauseVariants variants;
    private final Repos.ClauseVariantUsages usages;
    private final Repos.Contracts contracts;

    public ClauseController(Repos.ClauseConcepts concepts, Repos.ClauseVariants variants,
                            Repos.ClauseVariantUsages usages, Repos.Contracts contracts) {
        this.concepts = concepts;
        this.variants = variants;
        this.usages = usages;
        this.contracts = contracts;
    }

    @GetMapping("/concepts")
    public List<Map<String, Object>> conceptList() {
        Map<UUID, List<ClauseVariant>> byConcept = variants.findAll().stream()
                .collect(Collectors.groupingBy(v -> v.clauseConceptId));
        return concepts.findAll().stream().map(c -> {
            List<ClauseVariant> vs = byConcept.getOrDefault(c.id, List.of());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.id);
            m.put("conceptCode", c.conceptCode);
            m.put("name", c.name);
            m.put("category", c.category);
            m.put("riskCategory", c.riskCategory);
            m.put("isCore", c.isCore);
            m.put("variantCount", vs.size());
            m.put("tiers", vs.stream().map(v -> v.positionTier).distinct().sorted().toList());
            return m;
        }).toList();
    }

    @GetMapping("/concepts/{id}/variants")
    public List<Map<String, Object>> variantsForConcept(@PathVariable UUID id) {
        return variants.findByClauseConceptId(id).stream()
                .sorted(Comparator.comparingInt(v -> tierRank(v.positionTier)))
                .map(this::variantMap).toList();
    }

    @GetMapping("/variants")
    public List<Map<String, Object>> allVariants() {
        Map<UUID, String> conceptNames = new HashMap<>();
        concepts.findAll().forEach(c -> conceptNames.put(c.id, c.name));
        return variants.findAll().stream()
                .sorted(Comparator.comparing((com.acme.clm.domain.ClauseVariant v) ->
                        conceptNames.getOrDefault(v.clauseConceptId, "")).thenComparingInt(v -> tierRank(v.positionTier)))
                .map(v -> {
                    Map<String, Object> m = new LinkedHashMap<>(variantMap(v));
                    m.put("concept", conceptNames.get(v.clauseConceptId));
                    return m;
                }).toList();
    }

    @GetMapping("/variants/{id}/usage")
    public Map<String, Object> usage(@PathVariable UUID id) {
        ClauseVariant v = variants.findById(id).orElseThrow(() -> new ApiExceptions.NotFoundException("Variant not found"));
        List<Map<String, Object>> live = usages.findByClauseVariantId(id).stream().map(u -> {
            var c = contracts.findById(u.contractId).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("contractId", u.contractId);
            m.put("contractNumber", c == null ? null : c.contractNumber);
            m.put("title", c == null ? null : c.title);
            m.put("status", c == null ? null : c.status);
            m.put("wasModified", u.wasModified);
            return m;
        }).toList();
        return Map.of("variant", variantMap(v), "usedByContracts", live, "count", live.size());
    }

    private Map<String, Object> variantMap(ClauseVariant v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.id);
        m.put("conceptId", v.clauseConceptId);
        m.put("jurisdiction", v.jurisdictionCode);
        m.put("language", v.languageCode);
        m.put("positionTier", v.positionTier);
        m.put("riskTier", v.riskTier);
        m.put("bodyText", v.bodyText);
        m.put("guidanceNotes", v.guidanceNotes);
        m.put("translationStatus", v.translationStatus);
        m.put("status", v.status);
        return m;
    }

    private int tierRank(String tier) {
        return switch (tier == null ? "" : tier) {
            case "PREFERRED" -> 0;
            case "ACCEPTABLE" -> 1;
            case "FALLBACK" -> 2;
            case "UNACCEPTABLE" -> 3;
            default -> 4;
        };
    }
}
