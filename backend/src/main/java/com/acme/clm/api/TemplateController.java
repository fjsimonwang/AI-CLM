package com.acme.clm.api;

import com.acme.clm.config.CurrentUser;
import com.acme.clm.repo.Repos;
import com.acme.clm.service.DraftingService;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final Repos.Templates templates;
    private final Repos.TemplateSections sections;
    private final Repos.ClauseConcepts concepts;
    private final Repos.ClauseVariants variants;
    private final DraftingService drafting;
    private final CurrentUser current;

    public TemplateController(Repos.Templates templates, Repos.TemplateSections sections, Repos.ClauseConcepts concepts,
                              Repos.ClauseVariants variants, DraftingService drafting, CurrentUser current) {
        this.templates = templates;
        this.sections = sections;
        this.concepts = concepts;
        this.variants = variants;
        this.drafting = drafting;
        this.current = current;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return templates.findAll().stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.id);
            m.put("name", t.name);
            m.put("contractType", t.contractTypeCode);
            m.put("jurisdiction", t.jurisdictionCode);
            m.put("language", t.languageCode);
            m.put("version", t.versionNo);
            m.put("status", t.status);
            m.put("description", t.description);
            m.put("tags", t.tags);
            m.put("legalEntityId", t.legalEntityId);
            m.put("sectionCount", sections.findByTemplateIdOrderBySortOrder(t.id).size());
            return m;
        }).toList();
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable UUID id) {
        var t = templates.findById(id).orElseThrow();
        List<Map<String, Object>> secs = sections.findByTemplateIdOrderBySortOrder(id).stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sortOrder", s.sortOrder);
            m.put("heading", s.heading);
            m.put("isOptional", s.isOptional);
            m.put("concept", s.clauseConceptId == null ? null
                    : concepts.findById(s.clauseConceptId).map(c -> c.name).orElse(null));
            m.put("defaultVariantTier", s.defaultClauseVariantId == null ? null
                    : variants.findById(s.defaultClauseVariantId).map(v -> v.positionTier).orElse(null));
            m.put("staticBody", s.staticBody);
            return m;
        }).toList();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.id);
        m.put("name", t.name);
        m.put("contractType", t.contractTypeCode);
        m.put("jurisdiction", t.jurisdictionCode);
        m.put("language", t.languageCode);
        m.put("description", t.description);
        m.put("tags", t.tags);
        m.put("bodyHtml", t.bodyHtml);
        m.put("sections", secs);
        return m;
    }

    @PostMapping("/assemble/{contractId}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('PERM_EDIT_DOCUMENT')")
    public DraftingService.DraftResult assemble(@PathVariable UUID contractId) {
        return drafting.assemble(contractId, current.id());
    }
}
