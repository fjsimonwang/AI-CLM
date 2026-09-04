package com.acme.clm.service;

import com.acme.clm.ai.AiService;
import com.acme.clm.common.ApiExceptions;
import com.acme.clm.common.Json;
import com.acme.clm.domain.Contract;
import com.acme.clm.domain.ContractBriefing;
import com.acme.clm.domain.IntakeSession;
import com.acme.clm.domain.WorkflowTask;
import com.acme.clm.repo.Repos;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Generates and stores the AI approver briefing per contract, shared between the Approvals
 * page and the contract page. Briefings are regenerated when the contract row is updated
 * (e.g. after resubmit) or explicitly forced.
 */
@Service
public class BriefingService {

    private static final Logger log = LoggerFactory.getLogger(BriefingService.class);
    private static final String PROMPT_VERSION = "brief-v2";

    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    private final Repos.ContractBriefings briefings;
    private final Repos.Contracts contracts;
    private final Repos.WorkflowTasks tasks;
    private final Repos.WorkflowInstances instances;
    private final Repos.IntakeSessions intakeSessions;
    private final Repos.LegalEntities entities;
    private final AiService ai;

    public BriefingService(Repos.ContractBriefings briefings, Repos.Contracts contracts,
                           Repos.WorkflowTasks tasks, Repos.WorkflowInstances instances,
                           Repos.IntakeSessions intakeSessions, Repos.LegalEntities entities,
                           AiService ai) {
        this.briefings = briefings;
        this.contracts = contracts;
        this.tasks = tasks;
        this.instances = instances;
        this.intakeSessions = intakeSessions;
        this.entities = entities;
        this.ai = ai;
    }

    /** Map shape returned to the frontend: {briefing, generatedAt}. */
    public Map<String, Object> view(ContractBriefing b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("briefing", Json.readMap(b.briefing));
        m.put("generatedAt", b.createdAt.toString());
        return m;
    }

    public boolean isFresh(ContractBriefing b, Contract c) {
        return b != null && (c.updatedAt == null || !b.createdAt.isBefore(c.updatedAt));
    }

    /** Returns the stored briefing if still fresh, else generates and stores a new one. */
    public Map<String, Object> getOrCreate(UUID contractId, UUID userId, boolean force) {
        Contract c = contracts.findById(contractId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Contract not found"));
        ContractBriefing b = briefings.findByContractId(contractId).orElse(null);
        if (!force && isFresh(b, c)) {
            Map<String, Object> out = view(b);
            out.put("cached", true);
            return out;
        }
        generateAndStore(c, userId);
        b = briefings.findByContractId(contractId).orElseThrow();
        Map<String, Object> out = view(b);
        out.put("cached", false);
        return out;
    }

    /** Existing stored briefings for the given contracts (no generation). */
    public Map<String, Object> existingFor(List<UUID> contractIds) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (UUID cid : contractIds) {
            briefings.findByContractId(cid)
                    .filter(b -> contracts.findById(cid).map(c -> isFresh(b, c)).orElse(false))
                    .ifPresent(b -> out.put(cid.toString(), view(b)));
        }
        return out;
    }

    /**
     * Queues background briefing generation for the user's open approval tasks that don't
     * already have a fresh briefing. Called on login. Returns the number queued.
     */
    public int prepareForUser(UUID userId) {
        int queued = 0;
        for (WorkflowTask t : tasks.findByAssignedUserIdAndStatus(userId, "OPEN")) {
            UUID contractId = instances.findById(t.workflowInstanceId).map(i -> i.contractId).orElse(null);
            if (contractId == null) continue;
            Contract c = contracts.findById(contractId).orElse(null);
            if (c == null) continue;
            if (isFresh(briefings.findByContractId(contractId).orElse(null), c)) continue;
            pool.submit(() -> {
                try {
                    generateAndStore(c, userId);
                } catch (Exception e) {
                    log.warn("Background briefing failed for {}: {}", contractId, e.toString());
                }
            });
            queued++;
        }
        return queued;
    }

    private void generateAndStore(Contract c, UUID userId) {
        String ctx = buildContext(c);
        AiService.ApproverBriefing ab = ai.approverBriefing(ctx, userId, c.id);
        ContractBriefing b = briefings.findByContractId(c.id).orElseGet(() -> {
            ContractBriefing nb = new ContractBriefing();
            nb.contractId = c.id;
            return nb;
        });
        b.briefing = Json.write(ab);
        b.createdAt = Instant.now();
        b.createdBy = userId;
        briefings.save(b);
    }

    private String buildContext(Contract c) {
        StringBuilder sb = new StringBuilder(contractCtx(c));
        String intake = intakeCtx(c);
        if (intake != null) sb.append("\n\n").append(intake);
        return sb.toString();
    }

    private String contractCtx(Contract c) {
        Map<String, Object> attrs = Json.readMap(c.typeAttributes);
        Map<String, Object> compact = new LinkedHashMap<>();
        attrs.forEach((k, v) -> { if (v != null && !String.valueOf(v).isBlank()) compact.put(k, v); });
        String entityName = c.contractingEntityId == null ? "n/a"
                : entities.findById(c.contractingEntityId).map(e -> e.legalName).orElse(c.contractingEntityId.toString());
        String paymentTerms = c.paymentTermsDays == null ? "not set" : c.paymentTermsDays + " days";
        // NOTE: parenthesise the whole concatenation before .formatted — `.formatted` binds tighter
        // than `+`, so without the parens only the last literal receives the arguments and every
        // field lands in the wrong %s (this put the entity UUID into "payment terms … days").
        return ("Contract %s (%s), entity %s, governing law %s, status %s, value %s %s, annual value %s, "
                + "effective %s, expiry %s, payment terms %s. type_attributes: %s. Existing note: %s")
                .formatted(c.contractNumber, c.contractTypeCode, entityName, c.governingLawCode,
                        c.status, c.valueAmount, c.currency, c.annualValueAmount,
                        c.effectiveDate, c.expiryDate, paymentTerms,
                        Json.write(compact), c.summary);
    }

    private String intakeCtx(Contract c) {
        if (c.intakeSessionId == null) return null;
        return intakeSessions.findById(c.intakeSessionId).map(s -> {
            List<Map<String, Object>> history = Json.readListOfMaps(s.conversationHistory);
            if (history.isEmpty()) return null;
            StringBuilder sb = new StringBuilder(
                    "INTAKE REQUEST HISTORY (the original conversational request that produced this contract):\n");
            for (var h : history) {
                String content = String.valueOf(h.get("content"));
                if (content.length() > 600) content = content.substring(0, 600) + "…";
                sb.append(h.get("role")).append(": ").append(content).append("\n");
            }
            return sb.toString();
        }).orElse(null);
    }
}