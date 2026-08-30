package com.acme.clm.service;

import com.acme.clm.common.ApiExceptions;
import com.acme.clm.common.Json;
import com.acme.clm.domain.*;
import com.acme.clm.repo.Repos;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Precedent-aware document assembly (plan §6B.3). Assembles from the approved template,
 * substitutes clause variants carried from an identified precedent where present, and
 * flags every carried-forward deviation (FALLBACK / UNACCEPTABLE) for acknowledgement.
 */
@Service
public class DraftingService {

    private final Repos.Contracts contracts;
    private final Repos.ContractTypes types;
    private final Repos.Templates templates;
    private final Repos.TemplateSections sections;
    private final Repos.MergeFields mergeFields;
    private final Repos.ClauseVariants variants;
    private final Repos.ClauseConcepts concepts;
    private final Repos.ClauseVariantUsages usages;
    private final Repos.ContractVersions versions;
    private final Repos.ContractParties contractParties;
    private final Repos.Parties parties;
    private final Repos.LegalEntities entities;
    private final Repos.PrecedentLinks precedents;
    private final WordEditorClient wordEditor;
    private final AuditService audit;

    public DraftingService(Repos.Contracts contracts, Repos.ContractTypes types, Repos.Templates templates,
                           Repos.TemplateSections sections, Repos.MergeFields mergeFields, Repos.ClauseVariants variants,
                           Repos.ClauseConcepts concepts, Repos.ClauseVariantUsages usages, Repos.ContractVersions versions,
                           Repos.ContractParties contractParties, Repos.Parties parties, Repos.LegalEntities entities,
                           Repos.PrecedentLinks precedents, WordEditorClient wordEditor, AuditService audit) {
        this.contracts = contracts;
        this.types = types;
        this.templates = templates;
        this.sections = sections;
        this.mergeFields = mergeFields;
        this.variants = variants;
        this.concepts = concepts;
        this.usages = usages;
        this.versions = versions;
        this.contractParties = contractParties;
        this.parties = parties;
        this.entities = entities;
        this.precedents = precedents;
        this.wordEditor = wordEditor;
        this.audit = audit;
    }

    public record DraftResult(String bodyText, String bodyHtml, List<String> fromPrecedent, List<String> fromTemplate,
                              List<Map<String, Object>> deviationsToAcknowledge, UUID templateId, String templateName,
                              String editorDocumentId) {}

    /**
     * Pull the current editor content back into the latest contract version so AI review and
     * history reflect user edits made in the embedded editor. Returns true if content changed.
     */
    @Transactional
    public boolean syncFromEditor(UUID contractId, UUID actor) {
        Contract c = contracts.findById(contractId).orElse(null);
        if (c == null || "MIGRATED".equals(c.source) || c.editorDocumentId == null || c.editorDocumentId.isBlank())
            return false;
        String text = wordEditor.getText(c.editorDocumentId);
        if (text == null || text.strip().isEmpty()) return false;
        List<ContractVersion> vs = versions.findByContractIdOrderByVersionNoDesc(contractId);
        if (vs.isEmpty()) return false;
        ContractVersion v = vs.get(0);
        String updated = text.strip();
        String existing = v.bodyText == null ? "" : v.bodyText.strip();
        if (updated.equals(existing)) return false;
        v.bodyText = updated;
        v.changeSummary = "Edited in document editor";
        versions.save(v);
        audit.record("CONTRACT", contractId.toString(), "DOCUMENT_EDITED", actor, null,
                Map.of("versionNo", v.versionNo));
        return true;
    }

    @Transactional
    public DraftResult assemble(UUID contractId, UUID actor) {
        Contract c = contracts.findById(contractId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Contract not found"));
        Template tpl = resolveTemplate(c);
        if (tpl == null) throw new ApiExceptions.BadRequestException("No template available for " + c.contractTypeCode);

        Map<String, String> merge = mergeContext(c);

        // precedent clause set (by concept) if a PREFILL/DRAFTING precedent exists
        Map<UUID, ClauseVariant> precedentByConcept = new HashMap<>();
        UUID precedentContractId = precedents.findByContractId(contractId).stream()
                .findFirst().map(p -> p.precedentContractId).orElse(null);
        if (precedentContractId != null) {
            for (ClauseVariantUsage u : usages.findByContractId(precedentContractId)) {
                variants.findById(u.clauseVariantId).ifPresent(v -> precedentByConcept.put(v.clauseConceptId, v));
            }
        }

        StringBuilder body = new StringBuilder();
        StringBuilder html = new StringBuilder();
        List<String> fromPrecedent = new ArrayList<>();
        List<String> fromTemplate = new ArrayList<>();
        List<Map<String, Object>> deviations = new ArrayList<>();
        int n = 1;

        String heading1 = applyMerge(tpl.name.replaceAll(" —.*$", ""), merge) + " — " + c.title;
        body.append(applyMerge("{{entity_name}} — ", merge)).append(c.title).append("\n\n");
        html.append("<h1>").append(esc(heading1)).append("</h1>");
        html.append("<p>Between <strong>").append(esc(merge.getOrDefault("entity_name", "[Entity]")))
            .append("</strong> and <strong>").append(esc(merge.getOrDefault("counterparty_name", "[Counterparty]")))
            .append("</strong>.</p>");

        for (TemplateSection s : sections.findByTemplateIdOrderBySortOrder(tpl.id)) {
            body.append(n).append(". ").append(s.heading).append("\n");
            html.append("<h2>").append(n).append(". ").append(esc(s.heading)).append("</h2>");
            n++;
            String text;
            if (s.staticBody != null && !s.staticBody.isBlank()) {
                text = applyMerge(s.staticBody, merge);
                fromTemplate.add(s.heading);
            } else if (s.clauseConceptId != null) {
                ClauseVariant chosen = null;
                boolean carried = false;
                ClauseVariant pv = precedentByConcept.get(s.clauseConceptId);
                if (pv != null && !"DEPRECATED".equals(pv.status)) {
                    chosen = pv;
                    carried = true;
                } else if (s.defaultClauseVariantId != null) {
                    chosen = variants.findById(s.defaultClauseVariantId).orElse(null);
                }
                if (chosen == null && s.clauseConceptId != null) {
                    chosen = variants.findByClauseConceptId(s.clauseConceptId).stream()
                            .min(Comparator.comparingInt(v -> tierRank(v.positionTier))).orElse(null);
                }
                text = chosen == null ? "[clause omitted]" : applyMerge(chosen.bodyText, merge);

                if (chosen != null) {
                    recordUsage(contractId, chosen.id, carried);
                    String conceptName = concepts.findById(s.clauseConceptId).map(x -> x.name).orElse(s.heading);
                    if (carried) {
                        fromPrecedent.add(conceptName + " (" + chosen.positionTier + ")");
                        if ("FALLBACK".equals(chosen.positionTier) || "UNACCEPTABLE".equals(chosen.positionTier)) {
                            deviations.add(Map.of(
                                    "concept", conceptName,
                                    "tier", chosen.positionTier,
                                    "reason", "Carried forward from precedent " + shortNum(precedentContractId)
                                            + "; " + ("UNACCEPTABLE".equals(chosen.positionTier)
                                            ? "outside playbook — blocks automated issue, route to legal."
                                            : "fallback position — needs explicit legal acknowledgement."),
                                    "variantId", chosen.id));
                        }
                    } else {
                        fromTemplate.add(conceptName);
                    }
                }
            } else {
                text = "[to be drafted]";
                fromTemplate.add(s.heading);
            }
            body.append(text).append("\n\n");
            for (String para : text.split("\n\n+")) {
                if (!para.isBlank()) html.append("<p>").append(esc(para.trim())).append("</p>");
            }
        }

        if (!deviations.isEmpty()) {
            html.append("<hr/><h2>Open points for legal review</h2><ul>");
            for (Map<String, Object> d : deviations) {
                html.append("<li><strong>").append(esc(String.valueOf(d.get("concept")))).append("</strong> (")
                    .append(esc(String.valueOf(d.get("tier")))).append(") — ")
                    .append(esc(String.valueOf(d.get("reason")))).append("</li>");
            }
            html.append("</ul>");
        }

        String htmlBody = html.toString();

        // persist as latest version body
        List<ContractVersion> vs = versions.findByContractIdOrderByVersionNoDesc(contractId);
        ContractVersion v = vs.isEmpty() ? newVersion(contractId, 1) : vs.get(0);
        v.bodyText = body.toString();
        v.changeSummary = "Assembled from " + tpl.name
                + (fromPrecedent.isEmpty() ? "" : "; " + fromPrecedent.size() + " clause(s) carried from precedent")
                + (deviations.isEmpty() ? "" : "; " + deviations.size() + " deviation(s) need acknowledgement");
        v.createdBy = actor;
        versions.save(v);

        // create or refresh the editable document in the word-editor service
        String docId = c.editorDocumentId;
        if (wordEditor.enabled()) {
            try {
                if (docId == null || docId.isBlank()) {
                    docId = wordEditor.createDocument(c.contractNumber + " — " + c.title, htmlBody, contractId.toString());
                    if (docId != null) { c.editorDocumentId = docId; contracts.save(c); }
                } else {
                    wordEditor.setContent(docId, htmlBody);
                }
            } catch (Exception e) { /* editor optional — draft still saved */ }
        }

        audit.record("CONTRACT", contractId.toString(), "DRAFT_ASSEMBLED", actor, null,
                Map.of("template", tpl.name, "deviations", deviations.size(),
                        "carriedFromPrecedent", fromPrecedent.size(),
                        "editorDocument", docId == null ? "n/a" : docId));

        return new DraftResult(body.toString(), htmlBody, fromPrecedent, distinct(fromTemplate),
                deviations, tpl.id, tpl.name, docId);
    }

    /**
     * Assembles the contract from an uploaded third-party paper instead of a template:
     * the paper's HTML becomes the document body verbatim.
     */
    @Transactional
    public DraftResult assemblePaper(UUID contractId, UUID actor, String title, String paperHtml, String paperFilename) {
        Contract c = contracts.findById(contractId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Contract not found"));
        String html = paperHtml == null ? "" : paperHtml;

        List<ContractVersion> vs = versions.findByContractIdOrderByVersionNoDesc(contractId);
        ContractVersion v = vs.isEmpty() ? newVersion(contractId, 1) : vs.get(0);
        v.bodyText = jsoupText(html);
        v.changeSummary = "Uploaded third-party paper" + (paperFilename == null || paperFilename.isBlank() ? "" : ": " + paperFilename);
        v.createdBy = actor;
        versions.save(v);

        String docId = c.editorDocumentId;
        if (wordEditor.enabled()) {
            try {
                if (docId == null || docId.isBlank()) {
                    docId = wordEditor.createDocument(c.contractNumber + " — " + title, html, contractId.toString());
                    if (docId != null) { c.editorDocumentId = docId; contracts.save(c); }
                } else {
                    wordEditor.setContent(docId, html);
                }
            } catch (Exception e) { /* editor optional — paper still saved */ }
        }

        audit.record("CONTRACT", contractId.toString(), "PAPER_ASSEMBLED", actor, null,
                Map.of("file", paperFilename == null ? "" : paperFilename,
                        "editorDocument", docId == null ? "n/a" : docId));

        return new DraftResult(v.bodyText, html, List.of(), List.of(), List.of(), null, null, docId);
    }

    private static String jsoupText(String html) {
        try { return org.jsoup.Jsoup.parse(html).body().text(); }
        catch (Exception e) { return html == null ? "" : html.replaceAll("<[^>]+>", " "); }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private Template resolveTemplate(Contract c) {
        if (c.templateId != null) return templates.findById(c.templateId).orElse(null);
        ContractTypeDefinition def = types.findById(c.contractTypeCode).orElse(null);
        if (def != null && def.defaultTemplateId != null) return templates.findById(def.defaultTemplateId).orElse(null);
        return templates.findByContractTypeCode(c.contractTypeCode).stream().findFirst().orElse(null);
    }

    private Map<String, String> mergeContext(Contract c) {
        Map<String, String> m = new HashMap<>();
        entities.findById(c.contractingEntityId).ifPresent(e -> {
            m.put("entity_name", e.legalName);
            m.put("entity_country", e.countryCode);
        });
        contractParties.findByContractId(c.id).stream().findFirst().ifPresent(cp ->
                parties.findById(cp.partyId).ifPresent(p -> m.put("counterparty_name", p.legalName)));
        Map<String, Object> attrs = Json.readMap(c.typeAttributes);
        attrs.forEach((k, val) -> { m.put("type_attributes." + k, String.valueOf(val)); m.put(k, String.valueOf(val)); });
        if (attrs.get("term_months") != null) m.put("term_months", String.valueOf(attrs.get("term_months")));
        if (c.governingLawCode != null) m.put("governing_law", c.governingLawCode);
        if (c.paymentTermsDays != null) m.putIfAbsent("payment_terms_days", String.valueOf(c.paymentTermsDays));
        m.putIfAbsent("counterparty_name", "[Counterparty]");
        m.putIfAbsent("term_months", "[term]");
        m.putIfAbsent("entity_name", "[Entity]");
        m.putIfAbsent("governing_law", "[governing law]");
        return m;
    }

    private String applyMerge(String text, Map<String, String> merge) {
        String out = text;
        for (Map.Entry<String, String> e : merge.entrySet()) {
            out = out.replace("{{" + e.getKey() + "}}", e.getValue());
        }
        return out;
    }

    private void recordUsage(UUID contractId, UUID variantId, boolean carried) {
        boolean exists = usages.findByContractId(contractId).stream().anyMatch(u -> u.clauseVariantId.equals(variantId));
        if (exists) return;
        ClauseVariantUsage u = new ClauseVariantUsage();
        u.contractId = contractId;
        u.clauseVariantId = variantId;
        u.wasModified = false;
        u.modificationDiff = carried ? "carried from precedent" : null;
        usages.save(u);
    }

    private ContractVersion newVersion(UUID contractId, int no) {
        ContractVersion v = new ContractVersion();
        v.contractId = contractId;
        v.versionNo = no;
        v.versionLabel = "Draft v" + no;
        return v;
    }

    private String shortNum(UUID contractId) {
        return contractId == null ? "?" : contracts.findById(contractId).map(x -> x.contractNumber).orElse("?");
    }

    private static List<String> distinct(List<String> in) { return new ArrayList<>(new LinkedHashSet<>(in)); }

    private static int tierRank(String tier) {
        return switch (tier == null ? "" : tier) {
            case "PREFERRED" -> 0; case "ACCEPTABLE" -> 1; case "FALLBACK" -> 2; case "UNACCEPTABLE" -> 3; default -> 4;
        };
    }
}
