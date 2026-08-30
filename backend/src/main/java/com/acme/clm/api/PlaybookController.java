package com.acme.clm.api;

import com.acme.clm.repo.Repos;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Read side of the uploaded playbook library (admin CRUD lives in /api/admin). */
@RestController
public class PlaybookController {

    private final Repos.Playbooks playbooks;
    private final Repos.ContractTypes types;
    private final Repos.LegalEntities entities;

    public PlaybookController(Repos.Playbooks playbooks, Repos.ContractTypes types, Repos.LegalEntities entities) {
        this.playbooks = playbooks;
        this.types = types;
        this.entities = entities;
    }

    @GetMapping("/api/playbooks")
    public List<Map<String, Object>> list() {
        return playbooks.findAll().stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.id);
            m.put("name", p.name);
            m.put("contractType", p.contractTypeCode == null ? null
                    : types.findById(p.contractTypeCode).map(t -> t.displayName).orElse(p.contractTypeCode));
            m.put("contractTypeCode", p.contractTypeCode);
            m.put("legalEntityId", p.legalEntityId);
            m.put("entity", p.legalEntityId == null ? null
                    : entities.findById(p.legalEntityId).map(e -> e.shortName).orElse(null));
            m.put("jurisdiction", p.jurisdiction);
            m.put("language", p.language);
            m.put("description", p.description);
            m.put("isActive", p.isActive);
            m.put("createdAt", p.createdAt.toString());
            return m;
        }).toList();
    }

    @GetMapping("/api/playbooks/{id}")
    public Map<String, Object> get(@PathVariable java.util.UUID id) {
        var p = playbooks.findById(id).orElseThrow();
        return Map.of(
                "id", p.id,
                "name", p.name,
                "contractTypeCode", java.util.Optional.ofNullable(p.contractTypeCode).orElse(""),
                "legalEntityId", java.util.Optional.ofNullable(p.legalEntityId).map(Object::toString).orElse(""),
                "jurisdiction", java.util.Optional.ofNullable(p.jurisdiction).orElse(""),
                "language", p.language,
                "description", java.util.Optional.ofNullable(p.description).orElse(""),
                "bodyHtml", p.bodyHtml,
                "isActive", p.isActive);
    }
}