package com.acme.clm.api;

import com.acme.clm.ai.AiInteractionLog;
import com.acme.clm.ai.AiService;
import com.acme.clm.ai.LlmClient;
import com.acme.clm.common.ApiExceptions;
import com.acme.clm.common.Json;
import com.acme.clm.config.CurrentUser;
import com.acme.clm.domain.AiInteraction;
import com.acme.clm.domain.AiReviewRule;
import com.acme.clm.domain.ClauseVariant;
import com.acme.clm.domain.Contract;
import com.acme.clm.repo.Repos;
import com.acme.clm.service.AiInsightService;
import com.acme.clm.service.AuditService;
import com.acme.clm.service.AccessService;
import com.acme.clm.service.BriefingService;
import com.acme.clm.service.ReviewService;
import java.time.Instant;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final Repos.AiInteractions interactions;
    private final Repos.Contracts contracts;
    private final Repos.ClauseVariants clauseVariants;
    private final Repos.ClauseConcepts clauseConcepts;
    private final Repos.PrecedentLinks precedents;
    private final Repos.AiReviewRules reviewRules;
    private final AiService ai;
    private final AiInteractionLog aiLog;
    private final AuditService audit;
    private final CurrentUser current;
    private final BriefingService briefings;
    private final ReviewService review;
    private final AiInsightService insightService;
    private final AccessService access;
    private final com.acme.clm.service.PolicyService policies;

    public AiController(Repos.AiInteractions interactions, Repos.Contracts contracts, Repos.ClauseVariants clauseVariants,
                        Repos.ClauseConcepts clauseConcepts, AiService ai, AiInteractionLog aiLog,
                        AuditService audit, CurrentUser current, Repos.PrecedentLinks precedents,
                        Repos.AiReviewRules reviewRules,
                        BriefingService briefings, ReviewService review, AiInsightService insightService,
                        AccessService access, com.acme.clm.service.PolicyService policies) {
        this.policies = policies;
        this.interactions = interactions;
        this.contracts = contracts;
        this.clauseVariants = clauseVariants;
        this.clauseConcepts = clauseConcepts;
        this.ai = ai;
        this.aiLog = aiLog;
        this.audit = audit;
        this.current = current;
        this.precedents = precedents;
        this.reviewRules = reviewRules;
        this.briefings = briefings;
        this.review = review;
        this.insightService = insightService;
        this.access = access;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("modelLive", ai.modelIsLive());
    }

    /** Interaction payloads quote contract content (questions, AI replies) — same visibility as the contract. */
    private boolean interactionVisible(UUID cid) {
        if (cid == null) return true;
        return contracts.findById(cid)
                .map(c -> access.canView(current.id(), c))
                .orElse(false);
    }

    @GetMapping("/interactions")
    public List<Map<String, Object>> recent(@RequestParam(required = false) UUID contractId) {
        if (contractId != null) {
            Contract c = contracts.findById(contractId)
                    .orElseThrow(() -> new ApiExceptions.NotFoundException("Contract not found"));
            if (!access.canView(current.id(), c))
                throw new ApiExceptions.NotFoundException("Contract not found");
        }
        List<AiInteraction> list = contractId == null
                ? interactions.findTop200ByOrderByOccurredAtDesc()
                : interactions.findByContractIdOrderByOccurredAtDesc(contractId);
        return list.stream()
                .filter(i -> interactionVisible(i.contractId))
                .map(this::map).toList();
    }

    @PostMapping("/interactions/{id}/outcome")
    public Map<String, Object> outcome(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        aiLog.markOutcome(id, String.valueOf(body.getOrDefault("outcome", "ACCEPTED")), body.get("editedDelta"));
        return interactions.findById(id).map(this::map).orElseThrow();
    }

    @PostMapping("/interactions/{id}/revert")
    public Map<String, Object> revert(@PathVariable UUID id) {
        AiInteraction ai = interactions.findById(id).orElseThrow(() -> new ApiExceptions.NotFoundException("Not found"));
        ai.outcome = "REJECTED";
        ai.revertedAt = Instant.now();
        ai.revertedBy = current.id();
        interactions.save(ai);
        audit.recordAi(ai.contractId == null ? "AI_INTERACTION" : "CONTRACT",
                ai.contractId == null ? id.toString() : ai.contractId.toString(),
                "AI_CHANGE_REVERTED", current.id(), null, ai.inputHash);
        return map(ai);
    }

    public record SummarizeRequest(UUID contractId) {}

    @PostMapping("/summarize")
    public Map<String, Object> summarize(@RequestBody SummarizeRequest req) {
        var c = contracts.findById(req.contractId()).orElseThrow(() -> new ApiExceptions.NotFoundException("Contract not found"));

        // precedent: the closest linked precedent, else the most recent precedent link
        Contract pc = precedents.findByContractId(c.id).stream()
                .map(pl -> contracts.findById(pl.precedentContractId).orElse(null))
                .filter(Objects::nonNull)
                .findFirst().orElse(null);

        String contractCtx = contractCtx(c);
        String precedentCtx = pc == null
                ? "(no precedent — this contract was not modelled on another contract)"
                : "PRECEDENT " + contractCtx(pc);
        String diffCtx = "FIELD DIFF (precedent → current):\n" + diff(pc, c);

        AiService.StructuredSummary s = ai.summarize(contractCtx, precedentCtx, diffCtx, current.id(), c.id);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("text", s.summary());
        summary.put("differences", s.differences());
        summary.put("approverAttention", s.approverAttention());
        return Map.of("summary", summary, "modelLive", ai.modelIsLive());
    }

    private String contractCtx(Contract c) {
        Map<String, Object> attrs = Json.readMap(c.typeAttributes);
        Map<String, Object> compact = new LinkedHashMap<>();
        attrs.forEach((k, v) -> { if (v != null && !String.valueOf(v).isBlank()) compact.put(k, v); });
        return "Contract %s (%s), entity %s, governing law %s, status %s, value %s %s, annual value %s, "
                + "effective %s, expiry %s, payment terms %s days. type_attributes: %s. Existing note: %s"
                .formatted(c.contractNumber, c.contractTypeCode, c.contractingEntityId, c.governingLawCode,
                        c.status, c.valueAmount, c.currency, c.annualValueAmount,
                        c.effectiveDate, c.expiryDate, c.paymentTermsDays,
                        Json.write(compact), c.summary);
    }

    /** Deterministic field-by-field diff between a contract and its precedent. */
    private List<Map<String, Object>> diff(Contract pc, Contract cc) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (pc == null) return out;
        Map<String, Object> a = Json.readMap(pc.typeAttributes);
        Map<String, Object> b = Json.readMap(cc.typeAttributes);
        record Pair(String topic, String pv, String cv) {}
        List<Pair> pairs = new ArrayList<>();
        pairs.add(new Pair("title", pc.title, cc.title));
        pairs.add(new Pair("status", pc.status, cc.status));
        pairs.add(new Pair("governing law", pc.governingLawCode, cc.governingLawCode));
        pairs.add(new Pair("effective date", String.valueOf(pc.effectiveDate), String.valueOf(cc.effectiveDate)));
        pairs.add(new Pair("expiry date", String.valueOf(pc.expiryDate), String.valueOf(cc.expiryDate)));
        pairs.add(new Pair("value", pc.valueAmount + " " + pc.currency, cc.valueAmount + " " + cc.currency));
        pairs.add(new Pair("payment terms", pc.paymentTermsDays + " days", cc.paymentTermsDays + " days"));
        for (var e : a.entrySet()) {
            String av = String.valueOf(e.getValue());
            String bv = b.get(e.getKey()) == null ? "(absent)" : String.valueOf(b.get(e.getKey()));
            pairs.add(new Pair(e.getKey(), av, bv));
        }
        for (Pair p : pairs) {
            if (!Objects.equals(p.pv, p.cv))
                out.add(Map.of("topic", p.topic, "precedent", p.pv, "current", p.cv));
        }
        return out;
    }

    public record ContractReviewRequest(UUID contractId, List<UUID> playbookIds, List<String> ruleCodes) {}

    /** Starts a background AI review run; the result is polled via GET /review-runs. */
    @PostMapping("/review-runs")
    @PreAuthorize("hasAuthority('PERM_VIEW_CONTRACTS')")
    public Map<String, Object> startReview(@RequestBody ContractReviewRequest req) {
        Map<String, Object> out = new LinkedHashMap<>(review.start(req.contractId(), current.id(), req.playbookIds(), req.ruleCodes()));
        out.put("modelLive", ai.modelIsLive());
        return out;
    }

    /** Synchronous quick check — mandatory pre-submit AI review for the requestor. */
    @PostMapping("/review-quick")
    @PreAuthorize("hasAuthority('PERM_VIEW_CONTRACTS')")
    public Map<String, Object> quickReview(@RequestBody ContractReviewRequest req) {
        return review.quickCheck(req.contractId(), current.id(), req.ruleCodes());
    }

    /** Latest review run for a contract (frontends poll this while a run is RUNNING). */
    @GetMapping("/review-runs")
    @PreAuthorize("hasAuthority('PERM_VIEW_CONTRACTS')")
    public Map<String, Object> latestReview(@RequestParam UUID contractId) {
        return review.latest(contractId);
    }

    /** The separately-maintained rule checklist (readable by anyone who can view contracts). */
    @GetMapping("/review-rules")
    public List<Map<String, Object>> reviewRules() {
        return reviewRules.findAllByOrderBySortAsc().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.id);
            m.put("code", r.code);
            m.put("label", r.label);
            m.put("instruction", r.instruction);
            m.put("severity", r.severity);
            m.put("isActive", r.isActive);
            m.put("sort", r.sort);
            return m;
        }).toList();
    }

    private boolean hasAuthority(String a) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(x -> x.getAuthority().equals(a));
    }

    public record DeviationRequest(UUID contractId, String proposedClause, String conceptCode) {}

    @PostMapping("/deviation")
    public Object deviation(@RequestBody DeviationRequest req) {
        String playbook = buildPlaybook(req.conceptCode());
        var j = ai.analyzeDeviation(req.proposedClause() == null ? "" : req.proposedClause(),
                playbook, current.id(), req.contractId());
        return Json.mapper().convertValue(j, Object.class);
    }

    /** Shared briefing: stored per contract, reused by the Approvals page and the contract page. */
    @PostMapping("/approver-briefing")
    public Map<String, Object> approverBriefing(@RequestBody SummarizeRequest req,
                                                @RequestParam(defaultValue = "false") boolean force) {
        Map<String, Object> out = new LinkedHashMap<>(briefings.getOrCreate(req.contractId(), current.id(), force));
        out.put("modelLive", ai.modelIsLive());
        return out;
    }

    @GetMapping("/briefings")
    public Map<String, Object> briefings(@RequestParam List<UUID> contractIds) {
        return briefings.existingFor(contractIds);
    }

    @PostMapping("/briefings/prepare")
    public Map<String, Object> prepareBriefings() {
        return Map.of("queued", briefings.prepareForUser(current.id()));
    }

    /**
     * The user's dashboard AI insight. Returns the cached insight, or kicks off background
     * generation and returns pending=true (the frontend polls until ready).
     */
    @GetMapping("/insight")
    public Map<String, Object> insight(@RequestParam(defaultValue = "false") boolean force) {
        Map<String, Object> out = new LinkedHashMap<>(insightService.view(current.id(), force));
        out.put("modelLive", ai.modelIsLive());
        return out;
    }

    /** The heavier "deeper analysis" insight — generated only when the user asks for it. */
    @GetMapping("/insight/deep")
    public Map<String, Object> insightDeep(@RequestParam(defaultValue = "false") boolean force) {
        Map<String, Object> out = new LinkedHashMap<>(insightService.deepView(current.id(), force));
        out.put("modelLive", ai.modelIsLive());
        return out;
    }

    public record HelpRequest(String message, String page, String pageContext, List<Map<String, String>> history) {}

    /** Q&A about using the platform (navigation, operations, support, lifecycle). Advisory only. */
    @PostMapping("/help")
    public Map<String, Object> help(@RequestBody HelpRequest req) {
        List<LlmClient.Message> history = new ArrayList<>();
        if (req.history() != null) {
            for (Map<String, String> m : req.history()) {
                String role = String.valueOf(m.getOrDefault("role", "user"));
                String content = String.valueOf(m.getOrDefault("content", ""));
                if (content.isBlank()) continue;
                if ("assistant".equals(role)) history.add(LlmClient.Message.assistant(content));
                else history.add(LlmClient.Message.user(content));
            }
        }
        String policyContext = policies.helpContext(current.id());
        AiService.HelpTurn t = ai.helpChat(String.valueOf(req.message()), history, req.page(),
                req.pageContext(), policyContext, current.id());
        return Map.of("reply", t.reply(), "interactionId", t.interactionId(), "modelLive", ai.modelIsLive());
    }

    private String buildPlaybook(String conceptCode) {
        if (conceptCode == null) return "(no specific concept selected)";
        var concept = clauseConcepts.findByConceptCode(conceptCode).orElse(null);
        if (concept == null) return "(unknown concept)";
        StringBuilder sb = new StringBuilder(concept.name).append(":\n");
        for (ClauseVariant v : clauseVariants.findByClauseConceptId(concept.id)) {
            sb.append("- [").append(v.positionTier).append("] ").append(v.bodyText).append("\n");
        }
        return sb.toString();
    }

    private Map<String, Object> map(AiInteraction a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.id);
        m.put("surface", a.surface);
        m.put("capability", a.capability);
        m.put("promptId", a.promptId);
        m.put("promptVersion", a.promptVersion);
        m.put("confidence", a.confidenceScore);
        m.put("outcome", a.outcome);
        m.put("latencyMs", a.latencyMs);
        m.put("tokenCost", a.tokenCost);
        m.put("occurredAt", a.occurredAt);
        m.put("revertedAt", a.revertedAt);
        m.put("contractId", a.contractId);
        m.put("inputSummary", a.inputSummary == null ? null : Json.read(a.inputSummary));
        m.put("output", a.output == null ? null : Json.read(a.output));
        return m;
    }
}
