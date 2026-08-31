package com.acme.clm.service;

import com.acme.clm.ai.AiService;
import com.acme.clm.common.Json;
import com.acme.clm.domain.AiInteraction;
import com.acme.clm.domain.AuditEvent;
import com.acme.clm.domain.CommentThread;
import com.acme.clm.domain.Contract;
import com.acme.clm.domain.ContractRisk;
import com.acme.clm.domain.Obligation;
import com.acme.clm.domain.WorkflowTask;
import com.acme.clm.repo.Repos;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The dashboard AI insight: gathers everything the system has logged about the user's work
 * (tasks, contracts, risks, obligations, audit trail, AI interactions) and asks the model for
 * highlights, suspicious patterns and actionable suggestions. One insight per user per day,
 * triggered at login and on the first dashboard visit of the day, so tokens are spent once
 * daily rather than repeatedly.
 */
@Service
public class AiInsightService {

    private static final Logger log = LoggerFactory.getLogger(AiInsightService.class);

    private final ExecutorService pool = Executors.newFixedThreadPool(1);
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();
    private final Map<UUID, QuickCached> quickCache = new ConcurrentHashMap<>();

    private record Cached(Instant generatedAt, AiService.Insight insight) {}
    private record QuickCached(Instant generatedAt, AiService.QuickInsight insight) {}

    private final Repos.Contracts contracts;
    private final Repos.ContractParticipants participants;
    private final Repos.WorkflowTasks tasks;
    private final Repos.WorkflowInstances instances;
    private final Repos.AuditEvents audits;
    private final Repos.AiInteractions interactions;
    private final Repos.Obligations obligations;
    private final Repos.ContractRisks risks;
    private final Repos.Users users;
    private final Repos.CommentThreads threads;
    private final Repos.CommentMessages messages;
    private final AiService ai;

    public AiInsightService(Repos.Contracts contracts, Repos.ContractParticipants participants,
                            Repos.WorkflowTasks tasks, Repos.WorkflowInstances instances,
                            Repos.AuditEvents audits, Repos.AiInteractions interactions,
                            Repos.Obligations obligations, Repos.ContractRisks risks, Repos.Users users,
                            Repos.CommentThreads threads, Repos.CommentMessages messages,
                            AiService ai) {
        this.contracts = contracts;
        this.participants = participants;
        this.tasks = tasks;
        this.instances = instances;
        this.audits = audits;
        this.interactions = interactions;
        this.obligations = obligations;
        this.risks = risks;
        this.users = users;
        this.threads = threads;
        this.messages = messages;
        this.ai = ai;
    }

    public record Signals(long openTasks, long overdueTasks, long highRisks, long overdueObligations,
                          long failingAi, long expiringSoon) {
        public boolean any() {
            return openTasks > 0 || overdueTasks > 0 || highRisks > 0 || overdueObligations > 0
                    || failingAi > 0 || expiringSoon > 0;
        }
    }

    /**
     * Kicked at login: makes sure an insight exists for today so it is ready by the time the
     * user reaches the dashboard. No-op when today's insight is already cached or generating.
     */
    public void onLogin(UUID userId) {
        kickQuick(userId);
    }

    /** The fast triage insight is regenerated when older than this — it is cheap to run. */
    private static final long QUICK_TTL_MINUTES = 20;

    private boolean freshToday(UUID userId) {
        Cached c = cache.get(userId);
        return c != null && c.generatedAt.atZone(ZoneId.systemDefault()).toLocalDate().equals(LocalDate.now());
    }

    private boolean quickFresh(UUID userId) {
        QuickCached c = quickCache.get(userId);
        return c != null && c.generatedAt.isAfter(Instant.now().minus(QUICK_TTL_MINUTES, ChronoUnit.MINUTES));
    }

    private void kickQuick(UUID userId) {
        if (quickFresh(userId) || quickInFlight.contains(userId)) return;
        quickInFlight.add(userId);
        pool.submit(() -> {
            try {
                quickCache.put(userId, new QuickCached(Instant.now(), ai.quickInsight(buildQuickContext(userId), userId)));
            } catch (Exception e) {
                log.warn("Quick insight generation failed for {}: {}", userId, e.toString());
            } finally {
                quickInFlight.remove(userId);
            }
        });
    }

    /**
     * The fast default dashboard insight: a short triage of the user's open tasks and attention
     * items, plus deterministic attention signals. Regenerated in the background when stale.
     */
    public Map<String, Object> view(UUID userId, boolean force) {
        if (force) quickCache.remove(userId);
        boolean stale = !quickFresh(userId);
        Signals signals = signalsFor(userId);
        if (stale) kickQuick(userId);
        QuickCached c = quickCache.get(userId);
        boolean fresh = c != null && !stale;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pending", !fresh);
        out.put("signals", signals);
        out.put("deepReady", freshToday(userId));
        if (fresh) {
            out.put("quick", Map.of("summary", c.insight.summary(), "items", c.insight.items()));
            out.put("generatedAt", c.generatedAt.toString());
        }
        return out;
    }

    /**
     * The heavier "deeper analysis" insight — full trace analysis (audit trail, AI activity,
     * contracts, risks, obligations). Once per day unless forced; generated only on demand.
     */
    public Map<String, Object> deepView(UUID userId, boolean force) {
        boolean stale = !freshToday(userId);
        // Don't auto-retry a failed run — the deep prompt is large and can time out; the user
        // retries explicitly (force) so we don't hammer the model on every poll.
        boolean lastFailed = deepFailed.contains(userId);
        if ((stale || force) && (force || (!inFlight.contains(userId) && !lastFailed))) {
            if (force) { cache.remove(userId); deepFailed.remove(userId); }
            inFlight.add(userId);
            pool.submit(() -> {
                try {
                    generate(userId);
                    deepFailed.remove(userId);
                } catch (Exception e) {
                    log.warn("Insight generation failed for {}: {}", userId, e.toString());
                    deepFailed.add(userId);
                } finally {
                    inFlight.remove(userId);
                }
            });
        }
        Cached c = cache.get(userId);
        boolean fresh = c != null && !stale;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pending", !fresh && !(deepFailed.contains(userId) && !inFlight.contains(userId)));
        out.put("failed", deepFailed.contains(userId) && !inFlight.contains(userId) && !fresh);
        if (fresh) {
            out.put("insight", Map.of(
                    "highlights", c.insight.highlights(),
                    "suspicious", c.insight.suspicious(),
                    "suggestions", c.insight.suggestions()));
            out.put("generatedAt", c.generatedAt.toString());
        }
        return out;
    }

    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private final Set<UUID> quickInFlight = ConcurrentHashMap.newKeySet();
    private final Set<UUID> deepFailed = ConcurrentHashMap.newKeySet();

    private void generate(UUID userId) {
        AiService.Insight insight = ai.insight(buildContext(userId), userId);
        cache.put(userId, new Cached(Instant.now(), insight));
    }

    /** Cheap, deterministic attention signals computed directly from the data. */
    private Signals signalsFor(UUID userId) {
        List<UUID> mine = contractIdsFor(userId);
        long openTasks = tasks.findByAssignedUserIdAndStatus(userId, "OPEN").size();
        long overdueTasks = tasks.findByAssignedUserIdAndStatus(userId, "OPEN").stream()
                .filter(t -> t.dueAt != null && t.dueAt.isBefore(Instant.now())).count();
        long highRisks = mine.stream()
                .mapToLong(cid -> risks.findByContractIdAndStatus(cid, "OPEN").stream()
                        .filter(r -> "HIGH".equals(r.severity) || "CRITICAL".equals(r.severity)).count())
                .sum();
        long overdueObligations = obligations.findByStatus("OPEN").stream()
                .filter(o -> mine.contains(o.contractId))
                .filter(o -> o.dueDate != null && o.dueDate.isBefore(LocalDate.now())).count();
        long failingAi = interactions.findTop200ByOrderByOccurredAtDesc().stream()
                .filter(i -> userId.equals(i.userId))
                .filter(i -> "REJECTED".equals(i.outcome) || "REVERTED".equals(i.outcome)
                        || (i.confidenceScore != null && i.confidenceScore.doubleValue() < 0.4))
                .count();
        long expiringSoon = contracts.findAll().stream()
                .filter(c -> mine.contains(c.id))
                .filter(c -> "ACTIVE".equals(c.status) || "IN_REVIEW".equals(c.status) || "DRAFT".equals(c.status))
                .filter(c -> c.expiryDate != null)
                .filter(c -> !c.expiryDate.isAfter(LocalDate.now().plusDays(14)))
                .count();
        return new Signals(openTasks, overdueTasks, highRisks, overdueObligations, failingAi, expiringSoon);
    }

    private List<UUID> contractIdsFor(UUID userId) {
        Set<UUID> mine = new HashSet<>();
        for (Contract c : contracts.findAll()) {
            if (userId.equals(c.ownerUserId) || userId.equals(c.createdBy) || userId.equals(c.assignedLawyerId))
                mine.add(c.id);
        }
        participants.findByUserId(userId).forEach(p -> mine.add(p.contractId));
        mine.remove(null);
        return new ArrayList<>(mine);
    }

    /**
     * Small, focused context for the fast triage insight: open tasks + the handful of things that
     * need the user's response. Deliberately excludes the audit trail, AI-activity log and the
     * full contract dump that make {@link #buildContext} slow.
     */
    private String buildQuickContext(UUID userId) {
        StringBuilder sb = new StringBuilder();
        users.findById(userId).ifPresent(u -> sb.append("USER: ").append(u.displayName)
                .append(" (roles: ").append(u.roles).append(")\n\n"));
        LocalDate today = LocalDate.now();
        Instant now = Instant.now();
        List<UUID> mine = contractIdsFor(userId);

        sb.append("MY OPEN WORKFLOW TASKS:\n");
        List<WorkflowTask> open = tasks.findByAssignedUserIdAndStatus(userId, "OPEN");
        if (open.isEmpty()) sb.append("(none)\n");
        for (WorkflowTask t : open) {
            UUID cid = instances.findById(t.workflowInstanceId).map(i -> i.contractId).orElse(null);
            Contract c = cid == null ? null : contracts.findById(cid).orElse(null);
            sb.append("- ").append(c == null ? "(unknown contract)" : c.contractNumber + " — " + c.title)
                    .append(", state ").append(t.stateKey).append(", type ").append(t.taskType)
                    .append(", due ").append(t.dueAt == null ? "n/a" : t.dueAt.toString())
                    .append(t.dueAt != null && t.dueAt.isBefore(now) ? " [OVERDUE]" : "")
                    .append(t.comments != null && !t.comments.isBlank() ? ", note: " + t.comments : "")
                    .append("\n");
        }

        sb.append("\nMY DRAFTS NOT YET SUBMITTED:\n");
        long drafts = 0;
        for (UUID cid : mine) {
            Contract c = contracts.findById(cid).orElse(null);
            if (c == null || !"DRAFT".equals(c.status)) continue;
            if (!userId.equals(c.ownerUserId) && !userId.equals(c.createdBy)) continue;
            sb.append("- ").append(c.contractNumber).append(" — ").append(c.title).append("\n");
            drafts++;
        }
        if (drafts == 0) sb.append("(none)\n");

        sb.append("\nMY CONTRACTS REJECTED OR CLOSED-REJECTED (need my attention):\n");
        long rej = 0;
        for (UUID cid : mine) {
            Contract c = contracts.findById(cid).orElse(null);
            if (c == null || !"CLOSED_REJECTED".equals(c.status)) continue;
            if (!userId.equals(c.ownerUserId) && !userId.equals(c.createdBy)) continue;
            sb.append("- ").append(c.contractNumber).append(" — ").append(c.title).append("\n");
            rej++;
        }
        if (rej == 0) sb.append("(none)\n");

        sb.append("\nDISCUSSION THREADS ON MY CONTRACTS AWAITING A REPLY (last message not mine):\n");
        int dc = 0;
        for (UUID cid : mine) {
            for (CommentThread th : threads.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("CONTRACT", cid.toString())) {
                if (!"OPEN".equals(th.status)) continue;
                var msgs = messages.findByThreadIdOrderByCreatedAtAsc(th.id);
                if (msgs.isEmpty()) continue;
                var last = msgs.get(msgs.size() - 1);
                if (userId.equals(last.authorUserId)) continue;
                if (dc++ >= 10) break;
                Contract c = contracts.findById(cid).orElse(null);
                sb.append("- \"").append(th.title).append("\" on ").append(c == null ? cid : c.contractNumber)
                        .append(" (").append(msgs.size()).append(" messages)\n");
            }
            if (dc >= 10) break;
        }
        if (dc == 0) sb.append("(none)\n");

        sb.append("\nIMMINENT EXPIRIES ON MY CONTRACTS (<= 30 days):\n");
        int ec = 0;
        for (UUID cid : mine) {
            Contract c = contracts.findById(cid).orElse(null);
            if (c == null || c.expiryDate == null) continue;
            if (c.expiryDate.isAfter(today.plusDays(30))) continue;
            if (ec++ >= 10) break;
            sb.append("- ").append(c.contractNumber).append(" — expires ").append(c.expiryDate)
                    .append(c.expiryDate.isBefore(today) ? " [EXPIRED]" : "").append("\n");
        }
        if (ec == 0) sb.append("(none)\n");

        sb.append("\nOVERDUE OBLIGATIONS ON MY CONTRACTS:\n");
        int oc = 0;
        for (Obligation o : obligations.findByStatus("OPEN")) {
            if (!mine.contains(o.contractId) || o.dueDate == null || !o.dueDate.isBefore(today)) continue;
            if (oc++ >= 10) break;
            Contract c = contracts.findById(o.contractId).orElse(null);
            sb.append("- ").append(o.description).append(", due ").append(o.dueDate)
                    .append(" on ").append(c == null ? o.contractId : c.contractNumber).append("\n");
        }
        if (oc == 0) sb.append("(none)\n");

        sb.append("\nOPEN HIGH/CRITICAL RISKS ON MY CONTRACTS:\n");
        int rc = 0;
        for (UUID cid : mine) {
            for (ContractRisk r : risks.findByContractIdAndStatus(cid, "OPEN")) {
                if (!"HIGH".equals(r.severity) && !"CRITICAL".equals(r.severity)) continue;
                if (rc++ >= 10) break;
                Contract c = contracts.findById(cid).orElse(null);
                sb.append("- [").append(r.severity).append("] ").append(r.title)
                        .append(" on ").append(c == null ? cid : c.contractNumber).append("\n");
            }
            if (rc >= 10) break;
        }
        if (rc == 0) sb.append("(none)\n");

        return sb.toString();
    }

    private String buildContext(UUID userId) {
        StringBuilder sb = new StringBuilder();
        users.findById(userId).ifPresent(u -> sb.append("USER PROFILE: ").append(u.displayName)
                .append(", roles: ").append(u.roles).append(", department: ").append(u.department).append("\n\n"));

        List<UUID> mine = contractIdsFor(userId);
        Map<UUID, Contract> byId = new LinkedHashMap<>();
        for (UUID cid : mine) contracts.findById(cid).ifPresent(c -> byId.put(cid, c));

        sb.append("OPEN WORKFLOW TASKS ASSIGNED TO ME:\n");
        List<WorkflowTask> open = tasks.findByAssignedUserIdAndStatus(userId, "OPEN");
        if (open.isEmpty()) sb.append("(none)\n");
        for (WorkflowTask t : open) {
            UUID cid = instances.findById(t.workflowInstanceId).map(i -> i.contractId).orElse(null);
            Contract c = cid == null ? null : byId.getOrDefault(cid, contracts.findById(cid).orElse(null));
            sb.append("- ").append(c == null ? "(unknown contract)" : c.contractNumber + " — " + c.title)
                    .append(", state ").append(t.stateKey)
                    .append(", due ").append(t.dueAt == null ? "n/a" : t.dueAt.toString())
                    .append(t.dueAt != null && t.dueAt.isBefore(Instant.now()) ? " [OVERDUE]" : "")
                    .append("\n");
        }

        sb.append("\nMY CONTRACTS (most recently updated first, max 15):\n");
        byId.values().stream()
                .sorted(Comparator.comparing((Contract c) -> c.updatedAt == null ? c.createdAt : c.updatedAt).reversed())
                .limit(15)
                .forEach(c -> sb.append("- ").append(c.contractNumber).append(" — ").append(c.title)
                        .append(", type ").append(c.contractTypeCode).append(", status ").append(c.status)
                        .append(", risk ").append(c.riskTier)
                        .append(", expires ").append(c.expiryDate == null ? "n/a" : c.expiryDate.toString())
                        .append(c.valueAmount != null ? ", value " + c.valueAmount + " " + c.currency : "")
                        .append("\n"));

        sb.append("\nOPEN RISKS ON MY CONTRACTS (max 20):\n");
        int rc = 0;
        for (UUID cid : mine) {
            for (ContractRisk r : risks.findByContractIdAndStatus(cid, "OPEN")) {
                if (rc++ >= 20) break;
                Contract c = byId.getOrDefault(cid, contracts.findById(cid).orElse(null));
                sb.append("- [").append(r.severity).append("][").append(r.source).append("] ")
                        .append(r.title)
                        .append(" on ").append(c == null ? cid : c.contractNumber)
                        .append(r.category != null ? " (category " + r.category + ")" : "")
                        .append(", opened ").append(r.createdAt.truncatedTo(ChronoUnit.DAYS)).append("\n");
            }
            if (rc >= 20) break;
        }
        if (rc == 0) sb.append("(none)\n");

        sb.append("\nOPEN OBLIGATIONS ON MY CONTRACTS, DUE SOON OR OVERDUE (max 15):\n");
        int oc = 0;
        for (Obligation o : obligations.findByStatus("OPEN")) {
            if (!mine.contains(o.contractId)) continue;
            if (o.dueDate == null || o.dueDate.isAfter(LocalDate.now().plusDays(30))) continue;
            if (oc++ >= 15) break;
            Contract c = byId.getOrDefault(o.contractId, contracts.findById(o.contractId).orElse(null));
            sb.append("- ").append(o.description)
                    .append(", due ").append(o.dueDate)
                    .append(o.dueDate.isBefore(LocalDate.now()) ? " [OVERDUE]" : "")
                    .append(" on ").append(c == null ? "(unknown)" : c.contractNumber).append("\n");
        }
        if (oc == 0) sb.append("(none)\n");

        sb.append("\nMY RECENT ACTIVITY (audit trail, max 30, newest first):\n");
        List<AuditEvent> acts = audits.findTop100ByOrderByOccurredAtDesc().stream()
                .filter(e -> userId.equals(e.actorUserId))
                .limit(30).toList();
        if (acts.isEmpty()) sb.append("(no recent actions)\n");
        for (AuditEvent e : acts)
            sb.append("- ").append(e.occurredAt.truncatedTo(ChronoUnit.MINUTES)).append(" ")
                    .append(e.action).append(" on ").append(e.entityType).append(" ")
                    .append(e.afterState != null ? e.afterState : "")
                    .append("\n");

        sb.append("\nMY RECENT AI INTERACTIONS (max 30, newest first):\n");
        List<AiInteraction> ais = interactions.findTop200ByOrderByOccurredAtDesc().stream()
                .filter(i -> userId.equals(i.userId))
                .limit(30).toList();
        if (ais.isEmpty()) sb.append("(no AI activity yet)\n");
        for (AiInteraction i : ais) {
            String contractRef = "";
            if (i.contractId != null) {
                Contract ref = byId.get(i.contractId);
                if (ref == null) ref = contracts.findById(i.contractId).orElse(null);
                contractRef = " (contract " + (ref == null ? i.contractId : ref.contractNumber) + ")";
            }
            sb.append("- ").append(i.occurredAt.truncatedTo(ChronoUnit.MINUTES)).append(" ")
                    .append(i.capability).append(" on surface ").append(i.surface)
                    .append(", outcome ").append(i.outcome)
                    .append(i.confidenceScore != null ? ", confidence " + i.confidenceScore : "")
                    .append(i.latencyMs != null ? ", " + i.latencyMs + "ms" : "")
                    .append(contractRef)
                    .append("\n");
        }

        return sb.toString();
    }
}