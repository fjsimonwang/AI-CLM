package com.acme.clm.service;

import com.acme.clm.common.ApiExceptions;
import com.acme.clm.common.Json;
import com.acme.clm.domain.*;
import com.acme.clm.repo.Repos;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractService {

    private final Repos.Contracts contracts;
    private final Repos.ContractTypes types;
    private final Repos.LegalEntities entities;
    private final Repos.Users users;
    private final Repos.Parties parties;
    private final Repos.ContractParties contractParties;
    private final Repos.ContractTerms terms;
    private final Repos.ContractVersions versions;
    private final Repos.Obligations obligations;
    private final Repos.PrecedentLinks precedents;
    private final Repos.ClauseVariantUsages clauseUsages;
    private final Repos.ClauseVariants clauseVariants;
    private final Repos.ClauseConcepts clauseConcepts;
    private final Repos.ContractParticipants participants;
    private final EffectiveTermsResolver effectiveTerms;
    private final AccessService access;
    private final AuditService audit;
    private final Repos.CommentThreads commentThreads;
    private final Repos.CommentMessages commentMessages;
    private final Repos.ContractRelations contractRelations;
    private final com.acme.clm.ai.AiService ai;
    private final WorkflowService workflow;
    private final Repos.IntakeSessions intakeSessions;

    public ContractService(Repos.Contracts contracts, Repos.ContractTypes types, Repos.LegalEntities entities,
                           Repos.Users users, Repos.Parties parties, Repos.ContractParties contractParties,
                           Repos.ContractTerms terms, Repos.ContractVersions versions, Repos.Obligations obligations,
                           Repos.PrecedentLinks precedents, Repos.ClauseVariantUsages clauseUsages,
                           Repos.ClauseVariants clauseVariants, Repos.ClauseConcepts clauseConcepts,
                           Repos.ContractParticipants participants, EffectiveTermsResolver effectiveTerms,
                           AccessService access, AuditService audit,
                           Repos.CommentThreads commentThreads, Repos.CommentMessages commentMessages,
                           Repos.ContractRelations contractRelations, com.acme.clm.ai.AiService ai,
                           WorkflowService workflow, Repos.IntakeSessions intakeSessions) {
        this.contracts = contracts;
        this.types = types;
        this.entities = entities;
        this.users = users;
        this.parties = parties;
        this.contractParties = contractParties;
        this.terms = terms;
        this.versions = versions;
        this.obligations = obligations;
        this.precedents = precedents;
        this.clauseUsages = clauseUsages;
        this.clauseVariants = clauseVariants;
        this.clauseConcepts = clauseConcepts;
        this.participants = participants;
        this.effectiveTerms = effectiveTerms;
        this.access = access;
        this.audit = audit;
        this.commentThreads = commentThreads;
        this.commentMessages = commentMessages;
        this.contractRelations = contractRelations;
        this.ai = ai;
        this.workflow = workflow;
        this.intakeSessions = intakeSessions;
    }

    public record CreateRequest(String contractTypeCode, String title, UUID contractingEntityId,
                                UUID counterpartyId, String counterpartyName, String governingLawCode,
                                Integer termMonths, Map<String, Object> typeAttributes,
                                UUID intakeSessionId, UUID precedentContractId, String summary,
                                UUID parentContractId, String relationshipType, List<String> participantUserIds) {}

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(UUID viewerId, String entityRegion, String type, String status,
                                          String counterparty, Integer expiringWithinDays,
                                          BigDecimal minValue, BigDecimal maxValue) {
        Map<String, Object> f = new LinkedHashMap<>();
        if (entityRegion != null) f.put("entity_region", entityRegion);
        if (type != null) f.put("contract_type_code", type);
        if (status != null) f.put("status", status);
        if (counterparty != null) f.put("counterparty_name", counterparty);
        if (expiringWithinDays != null) f.put("expiring_within_days", expiringWithinDays);
        if (minValue != null) f.put("min_value", minValue);
        if (maxValue != null) f.put("max_value", maxValue);
        return listFiltered(viewerId, f);
    }

    /**
     * Structured inquiry across every user-visible contract variable. Filter keys mirror the
     * NL_QUERY interpretation schema in AiService.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listFiltered(UUID viewerId, Map<String, Object> f) {
        String entityRegion = str(f.get("entity_region"));
        String type = str(f.get("contract_type_code"));
        String status = str(f.get("status"));
        String counterparty = str(f.get("counterparty_name"));
        Integer expiringWithinDays = intOf(f.get("expiring_within_days"));
        BigDecimal minValue = decimalOf(f.get("min_value"));
        BigDecimal maxValue = decimalOf(f.get("max_value"));
        String entityName = str(f.get("entity_name"));
        String governingLaw = str(f.get("governing_law"));
        String currency = str(f.get("currency"));
        String riskTier = str(f.get("risk_tier"));
        Integer minRisk = intOf(f.get("min_risk_score"));
        Boolean autoRenew = boolOf(f.get("auto_renew"));
        Integer effectiveWithinDays = intOf(f.get("effective_within_days"));
        Integer createdWithinDays = intOf(f.get("created_within_days"));
        String keyword = str(f.get("keyword"));
        Boolean isAmendment = boolOf(f.get("is_amendment"));

        Map<UUID, LegalEntity> entityById = entities.findAll().stream().collect(Collectors.toMap(e -> e.id, e -> e));
        LocalDate today = LocalDate.now();
        LocalDate horizon = expiringWithinDays == null ? null : today.plusDays(expiringWithinDays);
        LocalDate effectiveHorizon = effectiveWithinDays == null ? null : today.plusDays(effectiveWithinDays);
        Instant createdHorizon = createdWithinDays == null ? null : Instant.now().minusSeconds(createdWithinDays * 86400L);
        String kw = keyword == null ? null : keyword.toLowerCase();

        List<Contract> visible = viewerId == null ? contracts.findAll()
                : access.filterVisible(viewerId, contracts.findAll());

        return visible.stream()
                .filter(c -> type == null || type.equalsIgnoreCase(c.contractTypeCode))
                .filter(c -> status == null || status.equalsIgnoreCase(c.status))
                .filter(c -> {
                    if (entityRegion == null) return true;
                    LegalEntity e = entityById.get(c.contractingEntityId);
                    return e != null && regionMatches(entityRegion, e);
                })
                .filter(c -> expiringWithinDays == null
                        || (c.expiryDate != null && !c.expiryDate.isAfter(horizon) && !c.expiryDate.isBefore(today)))
                .filter(c -> minValue == null || (c.valueAmount != null && c.valueAmount.compareTo(minValue) >= 0))
                .filter(c -> maxValue == null || (c.valueAmount != null && c.valueAmount.compareTo(maxValue) <= 0))
                .filter(c -> counterparty == null || counterpartyMatches(c.id, counterparty))
                .filter(c -> {
                    if (entityName == null) return true;
                    LegalEntity e = entityById.get(c.contractingEntityId);
                    if (e == null) return false;
                    String n = entityName.toLowerCase();
                    return (e.shortName != null && e.shortName.toLowerCase().contains(n))
                            || (e.legalName != null && e.legalName.toLowerCase().contains(n));
                })
                .filter(c -> governingLaw == null || governingLaw.equalsIgnoreCase(c.governingLawCode))
                .filter(c -> currency == null || currency.equalsIgnoreCase(c.currency))
                .filter(c -> riskTier == null || riskTier.equalsIgnoreCase(c.riskTier))
                .filter(c -> minRisk == null || (c.riskScore != null && c.riskScore >= minRisk))
                .filter(c -> autoRenew == null || autoRenew == c.autoRenew)
                .filter(c -> effectiveWithinDays == null
                        || (c.effectiveDate != null && !c.effectiveDate.isBefore(today) && !c.effectiveDate.isAfter(effectiveHorizon)))
                .filter(c -> createdWithinDays == null
                        || (c.createdAt != null && !c.createdAt.isBefore(createdHorizon)))
                .filter(c -> kw == null
                        || (c.title != null && c.title.toLowerCase().contains(kw))
                        || (c.summary != null && c.summary.toLowerCase().contains(kw))
                        || (c.contractNumber != null && c.contractNumber.toLowerCase().contains(kw)))
                .filter(c -> isAmendment == null || isAmendment == (c.parentContractId != null))
                .sorted(Comparator.comparing((Contract c) -> c.valueAmount == null ? BigDecimal.ZERO : c.valueAmount).reversed())
                .map(c -> summary(c, entityById.get(c.contractingEntityId)))
                .toList();
    }

    private static String str(Object o) { return o == null || String.valueOf(o).isBlank() ? null : String.valueOf(o); }

    private static Integer intOf(Object o) {
        if (o == null) return null;
        try { return (int) Math.round(Double.parseDouble(String.valueOf(o))); } catch (Exception e) { return null; }
    }

    private static BigDecimal decimalOf(Object o) {
        if (o == null) return null;
        try { return new BigDecimal(String.valueOf(o)); } catch (Exception e) { return null; }
    }

    private static Boolean boolOf(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).toLowerCase();
        if (List.of("true", "yes", "1").contains(s)) return true;
        if (List.of("false", "no", "0").contains(s)) return false;
        return null;
    }

    private boolean regionMatches(String region, LegalEntity e) {
        String r = region.toUpperCase();
        if (r.equals("EU") || r.equals("EMEA") || r.equals("EUROPE"))
            return "EU".equalsIgnoreCase(e.dataResidencyRegion) || "UK".equalsIgnoreCase(e.dataResidencyRegion);
        if (r.equals("US") || r.equals("AMER") || r.equals("AMERICAS"))
            return "US".equalsIgnoreCase(e.dataResidencyRegion);
        return r.equalsIgnoreCase(e.dataResidencyRegion) || r.equalsIgnoreCase(e.countryCode);
    }

    private boolean counterpartyMatches(UUID contractId, String needle) {
        String n = needle.toLowerCase();
        for (ContractParty cp : contractParties.findByContractId(contractId)) {
            Party p = parties.findById(cp.partyId).orElse(null);
            if (p != null && ((p.legalName != null && p.legalName.toLowerCase().contains(n))
                    || (p.tradingName != null && p.tradingName.toLowerCase().contains(n)))) return true;
        }
        return false;
    }

    private Map<String, Object> summary(Contract c, LegalEntity e) {
        List<String> cps = contractParties.findByContractId(c.id).stream()
                .map(cp -> parties.findById(cp.partyId).map(p -> p.legalName).orElse("?"))
                .toList();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.id);
        m.put("contractNumber", c.contractNumber);
        m.put("title", c.title);
        m.put("type", c.contractTypeCode);
        m.put("status", c.status);
        m.put("entity", e == null ? null : e.shortName);
        m.put("entityName", e == null ? null : e.legalName);
        m.put("counterparties", cps);
        m.put("governingLaw", c.governingLawCode);
        m.put("valueAmount", c.valueAmount);
        m.put("currency", c.currency);
        m.put("effectiveDate", c.effectiveDate);
        m.put("expiryDate", c.expiryDate);
        m.put("autoRenew", c.autoRenew);
        m.put("riskTier", c.riskTier);
        m.put("riskScore", c.riskScore);
        m.put("source", c.source);
        m.put("summary", c.summary);
        m.put("rejectionReason", c.rejectionReason);
        m.put("updatedAt", c.updatedAt == null ? c.createdAt : c.updatedAt);
        m.put("updatedBy", userName(c.updatedBy == null ? c.createdBy : c.updatedBy));
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID id) { return get(id, null); }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID id, UUID viewerId) {
        Contract c = contracts.findById(id).orElseThrow(() -> new ApiExceptions.NotFoundException("Contract not found"));
        if (viewerId != null && !access.canView(viewerId, c)) {
            throw new ApiExceptions.ForbiddenException("You don't have access to this contract. Request access from the Access screen.");
        }
        LegalEntity e = entities.findById(c.contractingEntityId).orElse(null);
        Map<String, Object> m = new LinkedHashMap<>(summary(c, e));

        m.put("primaryLanguage", c.primaryLanguage);
        m.put("noticePeriodDays", c.noticePeriodDays);
        m.put("renewalTermMonths", c.renewalTermMonths);
        m.put("renewalType", c.renewalType);
        m.put("confidentialityLevel", c.confidentialityLevel);
        m.put("valueBasis", c.valueBasis);
        m.put("annualValueAmount", c.annualValueAmount);
        m.put("paymentTermsDays", c.paymentTermsDays);
        m.put("liabilitySummary", c.liabilitySummary);
        m.put("editorDocumentId", c.editorDocumentId);
        m.put("typeAttributes", Json.readMap(c.typeAttributes));
        m.put("owner", userName(c.ownerUserId));
        m.put("ownerUserId", c.ownerUserId);
        m.put("intakeSessionId", c.intakeSessionId);
        m.put("requestNumber", c.intakeSessionId == null ? null
                : intakeSessions.findById(c.intakeSessionId).map(s -> s.requestNumber).orElse(null));
        m.put("assignedLawyer", userName(c.assignedLawyerId));
        m.put("createdAt", c.createdAt);

        // parties
        m.put("parties", contractParties.findByContractId(id).stream().map(cp -> {
            Party p = parties.findById(cp.partyId).orElse(null);
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("partyId", cp.partyId);
            pm.put("legalName", p == null ? null : p.legalName);
            pm.put("role", cp.role);
            pm.put("country", p == null ? null : p.countryCode);
            pm.put("sanctionsStatus", p == null ? null : p.sanctionsCheckStatus);
            pm.put("signatoryName", cp.signatoryName);
            pm.put("signatoryEmail", cp.signatoryEmail);
            return pm;
        }).toList());

        // participants (explicit access)
        m.put("participants", participants.findByContractId(id).stream().map(pp -> {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("userId", pp.userId);
            pm.put("name", users.findById(pp.userId).map(u -> u.displayName).orElse("?"));
            pm.put("email", users.findById(pp.userId).map(u -> u.email).orElse(null));
            pm.put("role", pp.role);
            pm.put("addedAt", pp.addedAt);
            return pm;
        }).toList());

        // raw terms
        m.put("terms", terms.findByContractId(id).stream().map(t -> {
            Map<String, Object> tm = new LinkedHashMap<>();
            tm.put("key", t.termKey);
            tm.put("value", Json.mapper().convertValue(Json.read(t.termValue), Object.class));
            tm.put("type", t.termType);
            tm.put("inherited", t.isInherited);
            tm.put("effectiveFrom", t.effectiveFrom);
            tm.put("effectiveTo", t.effectiveTo);
            tm.put("extractionConfidence", t.extractionConfidence);
            tm.put("verified", t.verifiedBy != null);
            return tm;
        }).toList());

        // effective terms (resolved)
        m.put("effectiveTerms", effectiveTerms.resolve(id, LocalDate.now()).values());

        // versions
        m.put("versions", versions.findByContractIdOrderByVersionNoDesc(id).stream().map(v -> {
            Map<String, Object> vm = new LinkedHashMap<>();
            vm.put("versionNo", v.versionNo);
            vm.put("label", v.versionLabel);
            vm.put("changeSummary", v.changeSummary);
            vm.put("isExecuted", v.isExecuted);
            vm.put("createdAt", v.createdAt);
            vm.put("hasBody", v.bodyText != null && !v.bodyText.isBlank());
            return vm;
        }).toList());
        m.put("documentCount", versions.findByContractIdOrderByVersionNoDesc(id).size());

        // discussion (thread + reply counts for the contract)
        List<CommentThread> tlist = commentThreads
                .findByEntityTypeAndEntityIdOrderByCreatedAtDesc("CONTRACT", id.toString());
        int replyCount = tlist.stream()
                .mapToInt(t -> commentMessages.findByThreadIdOrderByCreatedAtAsc(t.id).size()).sum();
        m.put("discussionCount", replyCount);

        // obligations
        m.put("obligations", obligations.findByContractId(id).stream().map(this::obligationMap).toList());

        // hierarchy
        m.put("parentContractId", c.parentContractId);
        m.put("relationshipType", c.relationshipType);
        m.put("children", contracts.findByParentContractId(id).stream().map(ch -> Map.of(
                "id", ch.id, "contractNumber", ch.contractNumber, "title", ch.title,
                "relationshipType", ch.relationshipType == null ? "" : ch.relationshipType,
                "status", ch.status)).toList());

        // precedents
        m.put("precedents", precedents.findByContractId(id).stream().map(pl -> {
            Contract pc = contracts.findById(pl.precedentContractId).orElse(null);
            Map<String, Object> plm = new LinkedHashMap<>();
            plm.put("precedentContractId", pl.precedentContractId);
            plm.put("precedentNumber", pc == null ? null : pc.contractNumber);
            plm.put("precedentTitle", pc == null ? null : pc.title);
            plm.put("matchScore", pl.matchScore);
            plm.put("matchReasons", Json.read(pl.matchReasons));
            plm.put("usedFor", pl.usedFor);
            plm.put("acknowledged", pl.acknowledgedBy != null);
            return plm;
        }).toList());

        // clause usage
        m.put("clausesUsed", clauseUsages.findByContractId(id).stream().map(u -> {
            ClauseVariant v = clauseVariants.findById(u.clauseVariantId).orElse(null);
            ClauseConcept concept = v == null ? null : clauseConcepts.findById(v.clauseConceptId).orElse(null);
            Map<String, Object> um = new LinkedHashMap<>();
            um.put("variantId", u.clauseVariantId);
            um.put("concept", concept == null ? null : concept.name);
            um.put("positionTier", v == null ? null : v.positionTier);
            um.put("wasModified", u.wasModified);
            return um;
        }).toList());

        return m;
    }

    private Map<String, Object> obligationMap(Obligation o) {
        Map<String, Object> om = new LinkedHashMap<>();
        om.put("id", o.id);
        om.put("type", o.obligationType);
        om.put("description", o.description);
        om.put("dueDate", o.dueDate);
        om.put("status", o.status);
        om.put("owner", userName(o.ownerUserId));
        om.put("owningDepartment", o.owningDepartment);
        om.put("extractionConfidence", o.extractionConfidence);
        om.put("verified", o.verifiedBy != null);
        om.put("alertLeadDays", o.alertLeadDays);
        return om;
    }

    private String userName(UUID id) {
        return id == null ? null : users.findById(id).map(u -> u.displayName).orElse(null);
    }

    @Transactional
    public Map<String, Object> create(CreateRequest req, UUID actor) {
        ContractTypeDefinition def = types.findById(req.contractTypeCode())
                .orElseThrow(() -> new ApiExceptions.BadRequestException("Unknown contract type: " + req.contractTypeCode()));
        LegalEntity entity = entities.findById(req.contractingEntityId())
                .orElseThrow(() -> new ApiExceptions.BadRequestException("Unknown contracting entity"));

        Contract c = new Contract();
        c.contractTypeCode = def.code;
        c.title = req.title() == null || req.title().isBlank()
                ? def.displayName + (req.counterpartyName() != null ? " — " + req.counterpartyName() : "")
                : req.title();
        c.contractingEntityId = entity.id;
        c.status = "DRAFT";
        c.governingLawCode = req.governingLawCode() != null ? req.governingLawCode() : entity.defaultGoverningLaw;
        c.primaryLanguage = entity.defaultLanguage;
        c.source = "NATIVE";
        c.ownerUserId = actor;
        c.createdBy = actor;
        c.updatedBy = actor;
        c.templateId = def.defaultTemplateId;
        c.summary = req.summary();
        c.intakeSessionId = req.intakeSessionId();
        if (req.parentContractId() != null) {
            contracts.findById(req.parentContractId()).ifPresent(pc -> {
                c.parentContractId = pc.id;
                c.relationshipType = req.relationshipType() == null ? "CHILD_OF" : req.relationshipType();
            });
        }

        Map<String, Object> attrs = req.typeAttributes() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(req.typeAttributes());
        attrs.values().removeIf(Objects::isNull);
        if (req.termMonths() != null) attrs.putIfAbsent("term_months", req.termMonths());
        c.typeAttributes = Json.write(attrs);

        c.contractNumber = nextContractNumber(counterpartyPrefix(req.counterpartyName(), entity.shortName), def.code);
        c.riskScore = def.baseRisk;
        c.riskTier = def.baseRisk >= 50 ? "HIGH" : def.baseRisk >= 25 ? "MEDIUM" : "LOW";
        contracts.save(c);

        // counterparty
        UUID partyId = req.counterpartyId();
        if (partyId == null && req.counterpartyName() != null && !req.counterpartyName().isBlank()) {
            Party p = parties.findTop10ByLegalNameContainingIgnoreCaseOrTradingNameContainingIgnoreCase(
                            req.counterpartyName(), req.counterpartyName())
                    .stream().findFirst().orElse(null);
            if (p == null) {
                p = new Party();
                p.legalName = req.counterpartyName();
                p.partyType = "COUNTERPARTY".equals(def.category) ? "OTHER" : "VENDOR";
                p.sanctionsCheckStatus = "NOT_SCREENED";
                parties.save(p);
            }
            partyId = p.id;
        }
        if (partyId != null) {
            ContractParty cp = new ContractParty();
            cp.contractId = c.id;
            cp.partyId = partyId;
            cp.role = "COUNTERPARTY";
            contractParties.save(cp);
        }

        // term rows for key attributes
        if (req.termMonths() != null) {
            ContractTerm t = new ContractTerm();
            t.contractId = c.id;
            t.termKey = "term_months";
            t.termValue = Json.write(req.termMonths());
            t.termType = "NUMBER";
            t.effectiveFrom = LocalDate.now();
            terms.save(t);
        }

        // initial version
        ContractVersion v = new ContractVersion();
        v.contractId = c.id;
        v.versionNo = 1;
        v.versionLabel = "Draft v1";
        v.changeSummary = "Created" + (req.precedentContractId() != null ? " from precedent" : "");
        v.createdBy = actor;
        versions.save(v);

        // precedent link
        if (req.precedentContractId() != null) {
            Contract pc = contracts.findById(req.precedentContractId()).orElse(null);
            if (pc != null) {
                PrecedentLink pl = new PrecedentLink();
                pl.contractId = c.id;
                pl.precedentContractId = pc.id;
                pl.matchScore = BigDecimal.valueOf(0.9);
                pl.matchReasons = Json.write(List.of("Selected by requester during intake"));
                pl.usedFor = "PREFILL";
                precedents.save(pl);
            }
        }

        // participants (explicit access granted during intake)
        if (req.participantUserIds() != null) {
            for (String uidStr : req.participantUserIds()) {
                try {
                    UUID uid = UUID.fromString(uidStr);
                    if (uid.equals(actor)) continue;
                    ContractParticipant pp = new ContractParticipant();
                    pp.contractId = c.id;
                    pp.userId = uid;
                    pp.role = "VIEWER";
                    pp.addedBy = actor;
                    participants.save(pp);
                } catch (Exception ignored) { }
            }
        }

        audit.record("CONTRACT", c.id.toString(), "CREATED", actor, null, summary(c, entity));
        return get(c.id);
    }

    /**
     * Re-apply an intake session's captured fields onto an EXISTING draft contract — used when a
     * requester revises and resubmits a request that an approver returned to them. Keeps the
     * contract number, history and workflow lineage; refreshes the editable fields and bumps a
     * draft version. The editable document is deliberately left untouched — the requester's last
     * edited version is kept; they can rebuild it from the template with "Re-assemble" if they want.
     */
    @Transactional
    public Map<String, Object> updateFromIntake(UUID contractId, CreateRequest req, UUID actor) {
        Contract c = contracts.findById(contractId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Contract not found"));
        if (!"DRAFT".equals(c.status)) {
            throw new ApiExceptions.BadRequestException("Only a draft contract can be revised.");
        }
        LegalEntity entity = entities.findById(req.contractingEntityId())
                .orElseThrow(() -> new ApiExceptions.BadRequestException("Unknown contracting entity"));

        if (req.title() != null && !req.title().isBlank()) c.title = req.title();
        c.contractingEntityId = entity.id;
        if (req.governingLawCode() != null && !req.governingLawCode().isBlank()) c.governingLawCode = req.governingLawCode();

        Map<String, Object> attrs = req.typeAttributes() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(req.typeAttributes());
        attrs.values().removeIf(Objects::isNull);
        if (req.termMonths() != null) attrs.putIfAbsent("term_months", req.termMonths());
        c.typeAttributes = Json.write(attrs);
        c.updatedBy = actor;
        contracts.save(c);

        if (req.termMonths() != null) {
            terms.findByContractId(contractId).stream()
                    .filter(t -> "term_months".equals(t.termKey))
                    .forEach(terms::delete);
            ContractTerm t = new ContractTerm();
            t.contractId = contractId;
            t.termKey = "term_months";
            t.termValue = Json.write(req.termMonths());
            t.termType = "NUMBER";
            t.effectiveFrom = LocalDate.now();
            terms.save(t);
        }

        if (req.participantUserIds() != null) {
            for (String uidStr : req.participantUserIds()) {
                try {
                    UUID uid = UUID.fromString(uidStr);
                    if (uid.equals(actor)) continue;
                    if (participants.findById(new ContractParticipant.Key(contractId, uid)).isPresent()) continue;
                    ContractParticipant pp = new ContractParticipant();
                    pp.contractId = contractId;
                    pp.userId = uid;
                    pp.role = "VIEWER";
                    pp.addedBy = actor;
                    participants.save(pp);
                } catch (Exception ignored) { }
            }
        }

        int next = versions.findByContractIdOrderByVersionNoDesc(contractId).stream()
                .mapToInt(v -> v.versionNo).max().orElse(1) + 1;
        ContractVersion v = new ContractVersion();
        v.contractId = contractId;
        v.versionNo = next;
        v.versionLabel = "Draft v" + next;
        v.changeSummary = "Revised by requester after rejection";
        v.createdBy = actor;
        versions.save(v);

        audit.record("CONTRACT", contractId.toString(), "REVISED", actor, null, Map.of("version", next));
        return get(contractId);
    }

    @Transactional(readOnly = true)
    public String counterpartyName(UUID contractId) {
        return contractParties.findByContractId(contractId).stream()
                .filter(cp -> "COUNTERPARTY".equals(cp.role)).findFirst()
                .or(() -> contractParties.findByContractId(contractId).stream().findFirst())
                .flatMap(cp -> parties.findById(cp.partyId))
                .map(p -> p.legalName).orElse(null);
    }

    @Transactional
    public Map<String, Object> addParticipant(UUID contractId, UUID userId, String role, UUID actor) {
        contracts.findById(contractId).orElseThrow(() -> new ApiExceptions.NotFoundException("Contract not found"));
        users.findById(userId).orElseThrow(() -> new ApiExceptions.BadRequestException("Unknown user"));
        ContractParticipant p = participants.findById(new ContractParticipant.Key(contractId, userId))
                .orElseGet(ContractParticipant::new);
        p.contractId = contractId;
        p.userId = userId;
        p.role = role == null || role.isBlank() ? "VIEWER" : role;
        if (p.addedBy == null) p.addedBy = actor;
        participants.save(p);
        audit.record("CONTRACT", contractId.toString(), "PARTICIPANT_ADDED", actor, null,
                Map.of("user", userId.toString(), "role", p.role));
        return get(contractId);
    }

    @Transactional
    public Map<String, Object> removeParticipant(UUID contractId, UUID userId, UUID actor) {
        participants.deleteByContractIdAndUserId(contractId, userId);
        audit.record("CONTRACT", contractId.toString(), "PARTICIPANT_REMOVED", actor, null,
                Map.of("user", userId.toString()));
        return get(contractId);
    }

    @Transactional
    public Map<String, Object> updateStatus(UUID id, String status, UUID actor) {
        Contract c = contracts.findById(id).orElseThrow(() -> new ApiExceptions.NotFoundException("Contract not found"));
        String before = c.status;
        boolean requestor = actor != null && (actor.equals(c.ownerUserId) || actor.equals(c.createdBy));

        // All lifecycle actions below are the requestor's to take.
        switch (status) {
            case "DRAFT" -> { // recall from review
                if (!requestor) throw new ApiExceptions.ForbiddenException("Only the requestor can recall this contract to draft.");
                if (!"IN_REVIEW".equals(before)) throw new ApiExceptions.BadRequestException("Only a contract in review can be recalled to draft.");
                workflow.cancelOpen(id, actor);
            }
            case "CANCELLED" -> { // abandon a draft that was never rejected
                if (!requestor) throw new ApiExceptions.ForbiddenException("Only the requestor can cancel this request.");
                if (!"DRAFT".equals(before)) throw new ApiExceptions.BadRequestException("Only a draft can be cancelled.");
                if (c.rejectionReason != null) throw new ApiExceptions.BadRequestException("This request was rejected — close it instead of cancelling.");
            }
            case "CLOSED_REJECTED" -> { // close out a rejected request instead of revising it
                if (!requestor) throw new ApiExceptions.ForbiddenException("Only the requestor can close this request.");
                if (!"DRAFT".equals(before) || c.rejectionReason == null)
                    throw new ApiExceptions.BadRequestException("Only a rejected request can be closed.");
            }
            default -> throw new ApiExceptions.BadRequestException("Unsupported status change: " + status);
        }

        c.status = status;
        c.updatedBy = actor;
        c.updatedAt = java.time.Instant.now();
        contracts.save(c);
        audit.record("CONTRACT", id.toString(), "STATUS_CHANGED", actor,
                Map.of("status", before), Map.of("status", status));
        return get(id);
    }


    // ---------------- Relations ----------------

    private static final List<String> RELATION_TYPES =
            List.of("AMENDS", "AMENDED_BY", "SUPERSEDES", "SUPERSEDED_BY", "PRECEDES", "FOLLOWS",
                    "MASTER", "UNDER", "SIMILAR_FAMILY");

    private String reverseRelation(String type) {
        return switch (type == null ? "" : type) {
            case "AMENDS" -> "AMENDED_BY";
            case "AMENDED_BY" -> "AMENDS";
            case "SUPERSEDES" -> "SUPERSEDED_BY";
            case "SUPERSEDED_BY" -> "SUPERSEDES";
            case "PRECEDES" -> "FOLLOWS";
            case "FOLLOWS" -> "PRECEDES";
            case "MASTER" -> "UNDER";
            case "UNDER" -> "MASTER";
            default -> "SIMILAR_FAMILY";
        };
    }

    private Map<String, Object> relationRow(ContractRelation r, UUID viewerContractId) {
        // Normalize to the viewer contract's perspective: other = the other side of the row.
        boolean forward = r.contractId.equals(viewerContractId);
        UUID otherContractId = forward ? r.relatedContractId : r.contractId;
        String type = forward ? r.relationType : reverseRelation(r.relationType);
        Contract other = contracts.findById(otherContractId).orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.id);
        m.put("relationType", type);
        m.put("otherContractId", otherContractId);
        m.put("contractNumber", other == null ? null : other.contractNumber);
        m.put("title", other == null ? null : other.title);
        m.put("type", other == null ? null : other.contractTypeCode);
        m.put("status", other == null ? null : other.status);
        m.put("source", r.source);
        m.put("relationStatus", r.status);
        m.put("confidence", r.confidence);
        m.put("reasons", r.reasons == null ? List.of() : Json.read(r.reasons));
        m.put("createdAt", r.createdAt);
        m.put("decidedBy", userName(r.decidedBy));
        m.put("decidedAt", r.decidedAt);
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> relations(UUID id, UUID viewerId) {
        Contract c = contracts.findById(id).orElseThrow(() -> new ApiExceptions.NotFoundException("Contract not found"));
        if (viewerId != null && !access.canView(viewerId, c)) {
            throw new ApiExceptions.ForbiddenException("You don't have access to this contract.");
        }

        List<Map<String, Object>> all = new ArrayList<>();
        contractRelations.findByContractIdOrderByCreatedAtDesc(id)
                .forEach(r -> all.add(relationRow(r, id)));
        contractRelations.findByRelatedContractId(id)
                .stream().filter(r -> !r.contractId.equals(id))
                .forEach(r -> all.add(relationRow(r, id)));

        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> distinct = all.stream()
                .filter(m -> seen.add(m.get("otherContractId") + "|" + m.get("relationType")))
                .toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("relations", distinct);
        out.put("suggested", distinct.stream().filter(m -> "SUGGESTED".equals(m.get("relationStatus"))).toList());
        out.put("confirmed", distinct.stream()
                .filter(m -> "CONFIRMED".equals(m.get("relationStatus")) || "USER".equals(m.get("source"))).toList());
        return out;
    }

    @Transactional
    public Map<String, Object> detectRelations(UUID id, UUID userId) {
        Contract c = contracts.findById(id).orElseThrow(() -> new ApiExceptions.NotFoundException("Contract not found"));
        if (!access.canView(userId, c)) {
            throw new ApiExceptions.ForbiddenException("You don't have access to this contract.");
        }

        // IDs already covered: explicit hierarchy, precedent links, existing relation rows (both directions).
        Set<UUID> excluded = new HashSet<>();
        excluded.add(id);
        if (c.parentContractId != null) excluded.add(c.parentContractId);
        contracts.findByParentContractId(id).forEach(ch -> excluded.add(ch.id));
        precedents.findByContractId(id).forEach(pl -> excluded.add(pl.precedentContractId));
        contractRelations.findByContractIdOrderByCreatedAtDesc(id).forEach(r -> { excluded.add(r.relatedContractId); });
        contractRelations.findByRelatedContractId(id).forEach(r -> { excluded.add(r.contractId); });

        Set<UUID> myParties = contractParties.findByContractId(id).stream().map(cp -> cp.partyId).collect(Collectors.toSet());
        String myCounterparty = counterpartyName(id);

        // Strict candidate criteria: shared party, matching counterparty name, or same signing entity
        // plus an overlapping period.
        record Cand(Contract c, int score, List<String> reasons) {}
        List<Cand> scored = new ArrayList<>();
        for (Contract other : access.filterVisible(userId, contracts.findAll())) {
            if (excluded.contains(other.id)) continue;
            int score = 0;
            List<String> reasons = new ArrayList<>();

            Set<UUID> otherParties = contractParties.findByContractId(other.id).stream().map(cp -> cp.partyId).collect(Collectors.toSet());
            Set<UUID> shared = new HashSet<>(myParties); shared.retainAll(otherParties);
            if (!shared.isEmpty()) {
                score += 2;
                reasons.add("Shared party: " + parties.findById(shared.iterator().next()).map(p -> p.legalName).orElse("unknown"));
            }
            String otherCounterparty = counterpartyName(other.id);
            if (myCounterparty != null && otherCounterparty != null
                    && (otherCounterparty.toLowerCase().contains(myCounterparty.toLowerCase())
                        || myCounterparty.toLowerCase().contains(otherCounterparty.toLowerCase()))) {
                score += 2;
                reasons.add("Same counterparty entity: " + otherCounterparty);
            }
            if (c.contractingEntityId != null && c.contractingEntityId.equals(other.contractingEntityId)) {
                score += 1;
                reasons.add("Same signing entity: " + entities.findById(other.contractingEntityId).map(x -> x.shortName).orElse("?"));
            }
            // Period: the candidate was already effective when this contract starts (or windows overlap).
            boolean periodOverlap = c.effectiveDate != null && other.effectiveDate != null
                    && !other.effectiveDate.isAfter(c.effectiveDate)
                    && (other.expiryDate == null || other.expiryDate.isAfter(c.effectiveDate));
            if (c.effectiveDate != null && other.effectiveDate != null
                    && c.effectiveDate.equals(other.effectiveDate)) periodOverlap = true;
            if (periodOverlap) {
                score += 1;
                reasons.add("Active period covers " + c.effectiveDate + " (candidate " + other.effectiveDate
                        + "–" + other.expiryDate + ")");
            }
            if (score >= 3) scored.add(new Cand(other, score, reasons));
        }

        scored.sort(Comparator.comparingInt((Cand x) -> x.score).reversed());
        List<Cand> candidates = scored.stream().limit(8).toList();

        String currentDesc = "%s '%s' (%s), status %s, signed by %s, counterparty %s, effective %s – %s".formatted(
                c.contractTypeCode, c.title, c.contractNumber, c.status,
                entities.findById(c.contractingEntityId).map(x -> x.shortName).orElse("?"),
                myCounterparty == null ? "unknown" : myCounterparty, c.effectiveDate, c.expiryDate);
        StringBuilder candText = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            Contract o = candidates.get(i).c();
            candText.append("Candidate [%d]: %s '%s' (%s), status %s, signed by %s, counterparty %s, effective %s – %s%n   Evidence: %s%n".formatted(
                    i, o.contractTypeCode, o.title, o.contractNumber, o.status,
                    entities.findById(o.contractingEntityId).map(x -> x.shortName).orElse("?"),
                    counterpartyName(o.id), o.effectiveDate, o.expiryDate,
                    candidates.get(i).reasons()));
        }

        Map<Integer, com.acme.clm.ai.AiService.RelationSuggestion> classified = candidates.isEmpty()
                ? Map.of()
                : ai.classifyRelations(currentDesc, candText.toString(), userId, id);

        int created = 0;
        for (int i = 0; i < candidates.size(); i++) {
            Contract o = candidates.get(i).c();
            com.acme.clm.ai.AiService.RelationSuggestion s = classified.get(i);
            String type = s == null ? "SIMILAR_FAMILY" : s.relationType();
            double confidence = s == null ? 0.5 : s.confidence();
            if (!RELATION_TYPES.contains(type)) type = "SIMILAR_FAMILY";
            // Generic fallback when the model has no opinion: NDA before a later deal contract -> PRECEDES.
            if (s == null && "NDA".equals(o.contractTypeCode) && !"NDA".equals(c.contractTypeCode)) {
                type = "PRECEDES";
                confidence = 0.5;
            }
            boolean exists = contractRelations.findByContractIdOrderByCreatedAtDesc(id).stream()
                    .anyMatch(r -> r.relatedContractId.equals(o.id) && !r.status.equals("REJECTED"))
                    || contractRelations.findByRelatedContractId(o.id).stream()
                    .anyMatch(r -> r.contractId.equals(id) && !r.status.equals("REJECTED"));
            if (exists) continue;
            ContractRelation r = new ContractRelation();
            r.contractId = id;
            r.relatedContractId = o.id;
            r.relationType = type;
            r.source = "AI";
            r.status = "SUGGESTED";
            r.confidence = confidence;
            r.reasons = Json.write(s == null || s.reasons().isEmpty() ? candidates.get(i).reasons() : s.reasons());
            r.createdBy = userId;
            contractRelations.save(r);
            created++;
        }
        if (created > 0) {
            audit.record("CONTRACT", id.toString(), "RELATIONS_DETECTED", userId, null, Map.of("created", created));
        }

        Map<String, Object> out = new LinkedHashMap<>(relations(id, userId));
        out.put("detected", created);
        out.put("evaluated", candidates.size());
        return out;
    }

    @Transactional
    public Map<String, Object> decideRelation(UUID id, UUID relId, boolean confirm, UUID userId) {
        Contract c = contracts.findById(id).orElseThrow(() -> new ApiExceptions.NotFoundException("Contract not found"));
        if (!access.canView(userId, c)) {
            throw new ApiExceptions.ForbiddenException("You don't have access to this contract.");
        }
        ContractRelation r = contractRelations.findById(relId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Relation not found"));
        if (!id.equals(r.contractId) && !id.equals(r.relatedContractId)) {
            throw new ApiExceptions.NotFoundException("Relation does not belong to this contract");
        }
        r.status = confirm ? "CONFIRMED" : "REJECTED";
        r.decidedBy = userId;
        r.decidedAt = java.time.Instant.now();
        r.source = "USER";
        contractRelations.save(r);
        audit.record("CONTRACT", id.toString(), confirm ? "RELATION_CONFIRMED" : "RELATION_REJECTED",
                userId, null, Map.of("relation", relId.toString(), "type", r.relationType));
        return relations(id, userId);
    }

    /** Contract numbers carry the counterparty's name (e.g. NORTHWIND_TRADERS-MSA-2026-0006); our
     *  entity short name is only the fallback when no counterparty is known. */
    private String counterpartyPrefix(String name, String fallback) {
        if (name == null) return fallback;
        String s = name.toUpperCase()
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "")
                .trim();
        if (s.isBlank()) return fallback;
        if (s.length() > 30) s = s.substring(0, 30).replaceAll("_+$", "");
        return s;
    }

    private String nextContractNumber(String prefix, String type) {
        long seq = contracts.countByContractTypeCode(type) + 1;
        String candidate = "%s-%s-%d-%04d".formatted(prefix, type, Year.now().getValue(), seq);
        while (contracts.findByContractNumber(candidate).isPresent()) {
            seq++;
            candidate = "%s-%s-%d-%04d".formatted(prefix, type, Year.now().getValue(), seq);
        }
        return candidate;
    }
}
