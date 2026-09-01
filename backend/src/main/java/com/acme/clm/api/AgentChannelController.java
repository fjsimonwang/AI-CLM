package com.acme.clm.api;

import com.acme.clm.ai.AiService;
import com.acme.clm.common.ApiExceptions;
import com.acme.clm.common.HtmlSanitizer;
import com.acme.clm.config.CurrentUser;
import com.acme.clm.domain.AppUser;
import com.acme.clm.domain.CommentMessage;
import com.acme.clm.domain.CommentThread;
import com.acme.clm.domain.Contract;
import com.acme.clm.domain.UserSetting;
import com.acme.clm.repo.Repos;
import com.acme.clm.service.AccessService;
import com.acme.clm.service.AuditService;
import com.acme.clm.service.ContractService;
import java.util.*;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Agent-to-agent discussion channel (phase 1): invites the platform agent to answer a
 * question inside a contract discussion thread. Every turn is gated by the per-contract
 * switch, the per-user opt-in, and the caller's access to the contract.
 */
@RestController
@RequestMapping("/api/agent-channel")
@PreAuthorize("hasAuthority('PERM_COMMENT')")
public class AgentChannelController {

    private static final Logger log = LoggerFactory.getLogger(AgentChannelController.class);

    private final Repos.Contracts contracts;
    private final Repos.CommentThreads threads;
    private final Repos.CommentMessages messages;
    private final Repos.UserSettings settings;
    private final Repos.Users users;
    private final Repos.ContractParticipants participants;
    private final ContractService contractService;
    private final AccessService access;
    private final AuditService audit;
    private final AiService ai;
    private final HtmlSanitizer sanitizer;
    private final CurrentUser current;
    private final CommentController comments;

    private final ExecutorService autoAnswerExec = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "agent-auto-answer");
        t.setDaemon(true);
        return t;
    });

    private final java.util.concurrent.atomic.AtomicInteger autoInFlight =
            new java.util.concurrent.atomic.AtomicInteger();

    /** Threads whose agent turn is currently generating — drives the "An agent is replying"
     *  indicator BEFORE the message lands. Value is the start time (ms) for a stale-entry sweep. */
    private final java.util.Map<UUID, Long> agentBusyThreads = new java.util.concurrent.ConcurrentHashMap<>();

    private void markAgentBusy(UUID threadId) {
        long now = System.currentTimeMillis();
        agentBusyThreads.values().removeIf(started -> now - started > 180_000); // safety sweep
        agentBusyThreads.put(threadId, now);
    }
    private void clearAgentBusy(UUID threadId) { agentBusyThreads.remove(threadId); }

    @PreDestroy
    void shutdownExec() {
        autoAnswerExec.shutdownNow();
    }

    public AgentChannelController(Repos.Contracts contracts, Repos.CommentThreads threads,
                                  Repos.CommentMessages messages, Repos.UserSettings settings,
                                  Repos.Users users, Repos.ContractParticipants participants,
                                  ContractService contractService,
                                  AccessService access, AuditService audit, AiService ai,
                                  HtmlSanitizer sanitizer, CurrentUser current, CommentController comments) {
        this.contracts = contracts;
        this.threads = threads;
        this.messages = messages;
        this.settings = settings;
        this.users = users;
        this.participants = participants;
        this.contractService = contractService;
        this.access = access;
        this.audit = audit;
        this.ai = ai;
        this.sanitizer = sanitizer;
        this.current = current;
        this.comments = comments;
    }

    @GetMapping("/contracts/{contractId}/status")
    public Map<String, Object> status(@PathVariable UUID contractId) {
        loadVisible(contractId); // visibility check
        boolean userOptIn = optIn(current.id());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userOptIn", userOptIn);
        // Agent talk is governed solely by each user's personal opt-in (the header toggle).
        // It is available on any contract you can view once you have turned it on.
        out.put("active", userOptIn);
        out.put("busyThreads", agentBusyThreads.keySet().stream().map(UUID::toString).toList());
        return out;
    }

    public record AskRequest(UUID threadId, String question, UUID targetUserId) {}

    /** Personal agents available to answer on this contract (can view + opted in), for the ask composer. */
    @GetMapping("/contracts/{contractId}/agents")
    public List<Map<String, Object>> agents(@PathVariable UUID contractId) {
        Contract c = loadVisible(contractId);
        List<ParticipantAgent> pool = candidatePool(c);
        List<Map<String, Object>> out = new ArrayList<>();
        for (ParticipantAgent a : pool) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", a.userId);
            m.put("name", a.name);
            m.put("roleHint", a.roleHint);
            out.add(m);
        }
        return out;
    }

    /** Not @Transactional on purpose: LLM calls run outside any DB transaction so slow replies
     *  never pin a pooled connection, and a failing agent must not roll back the question or the
     *  answers that already succeeded. Each save commits independently; failures are collected. */
    @PostMapping("/contracts/{contractId}/ask")
    public Map<String, Object> ask(@PathVariable UUID contractId, @RequestBody AskRequest req) {
        UUID me = current.id();
        Contract c = loadVisible(contractId);
        if (!optIn(me)) {
            throw new ApiExceptions.BadRequestException("Turn on Agent talk (header toggle) to ask a participant's agent.");
        }
        if (req.threadId() == null) throw new ApiExceptions.BadRequestException("threadId is required");
        CommentThread t = threads.findById(req.threadId())
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Thread not found"));
        if (!"CONTRACT".equals(t.entityType) || !contractId.toString().equals(t.entityId)) {
            throw new ApiExceptions.BadRequestException("Thread does not belong to this contract.");
        }

        String question = sanitizer.plainText(sanitizer.clean(req.question() == null ? "" : req.question()));
        if (question.isBlank()) throw new ApiExceptions.BadRequestException("Question is empty");
        if (question.length() > 2000) throw new ApiExceptions.BadRequestException("Question is too long (max 2000 characters)");

        // persist the question as the asker's own message so the thread reads as a conversation
        CommentMessage q = new CommentMessage();
        q.threadId = t.id;
        q.authorUserId = me;
        q.channel = "HUMAN";
        q.bodyHtml = "<p>" + escape(question).replace("\n", "<br/>") + "</p>";
        messages.save(q);

        // pick who answers: the asker's explicit choice, else the best-placed agents
        List<ParticipantAgent> reps;
        if (req.targetUserId() != null) {
            if (req.targetUserId().equals(me))
                throw new ApiExceptions.BadRequestException("Pick another participant — your own agent can't answer for you.");
            ParticipantAgent target = candidatePool(c).stream()
                    .filter(a -> a.userId.equals(req.targetUserId()))
                    .findFirst()
                    .orElseThrow(() -> new ApiExceptions.BadRequestException(
                            "That person's agent is not available (they need contract access and their user's opt-in)."));
            reps = List.of(target);
        } else {
            // auto-routing answers for OTHERS — the asker's own agent doesn't answer their question
            reps = representatives(c, question, me);
        }
        if (reps.isEmpty()) {
            throw new ApiExceptions.BadRequestException(
                    "No other participant's agent is available to answer (agents need contract access and their user's opt-in).");
        }

        // The agents ACT on behalf of their humans: the selected/routed agent answers, then the
        // asker's own agent may review and clarify back and forth — bounded so it always ends.
        // This runs on a background executor so it keeps going (and the replies still land in the
        // thread) even if the asker navigates away; the client polls the thread for the results.
        boolean dialogue = reps.size() == 1 && optIn(me) && !me.equals(reps.get(0).userId);
        final UUID threadId = t.id;
        final List<UUID> repIds = reps.stream().map(a -> a.userId).toList();
        autoAnswerExec.submit(() -> {
            autoInFlight.incrementAndGet();
            try {
                runAsk(contractId, threadId, me, question, repIds, dialogue);
            } catch (Exception e) {
                log.warn("Async agent ask failed for contract {}: {}", contractId, e.toString());
            } finally {
                autoInFlight.decrementAndGet();
            }
        });
        return comments.threadView(t);
    }

    /** Background worker for {@link #ask}: re-loads the entities in this thread and runs each
     *  chosen representative's agent. Failures are best-effort — the question is already saved. */
    private void runAsk(UUID contractId, UUID threadId, UUID askerId, String question,
                        List<UUID> repIds, boolean dialogue) {
        Contract c = contracts.findById(contractId).orElse(null);
        CommentThread t = threads.findById(threadId).orElse(null);
        if (c == null || t == null) return;
        List<ParticipantAgent> pool = candidatePool(c);
        String askerName = userName(askerId);
        List<Map<String, Object>> turns = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        markAgentBusy(threadId);
        try {
            for (UUID repId : repIds) {
                ParticipantAgent rep = pool.stream().filter(a -> a.userId.equals(repId)).findFirst().orElse(null);
                if (rep == null) continue;
                try {
                    talk(c, t, askerId, dialogue, question, rep, askerName, turns);
                } catch (Exception e) {
                    failed.add(rep.name);
                }
            }
        } finally {
            clearAgentBusy(threadId);
        }
        Map<String, Object> auditAfter = new LinkedHashMap<>();
        auditAfter.put("threadId", threadId.toString());
        auditAfter.put("question", question);
        auditAfter.put("turns", turns);
        if (!failed.isEmpty()) auditAfter.put("failedFor", failed);
        audit.record("CONTRACT", contractId.toString(), "AGENT_CHANNEL_MESSAGE", askerId, null, auditAfter);
    }

    /**
     * Called by CommentController when a participant opens a thread on a contract whose
     * agent switch is ON: the best-placed OTHER participants' agents answer inline, with
     * no "✦ Ask agent" click. Runs on a background executor so the POST /threads response
     * isn't held by LLM latency; failures are best-effort and never surface to the poster.
     */
    public void autoAnswerNewThread(Contract c, CommentThread t, UUID askerId, String question) {
        if (!optIn(askerId) || question == null || question.isBlank()) return;
        if (autoInFlight.get() >= 8) return; // shed rather than queue unbounded — the human can still use "✦ Ask agent"
        autoAnswerExec.submit(() -> {
            autoInFlight.incrementAndGet();
            try {
                runAutoAnswer(c, t, askerId, question);
            } catch (Exception e) {
                // auto-answer is opportunistic: swallow, the human can still use "✦ Ask agent"
            } finally {
                autoInFlight.decrementAndGet();
            }
        });
    }

    private void runAutoAnswer(Contract c, CommentThread t, UUID askerId, String question) {
        // refresh the contract in this thread's persistence context
        Contract cc = contracts.findById(c.id).orElse(null);
        if (cc == null || !optIn(askerId)) return;
        List<ParticipantAgent> reps = representatives(cc, question, askerId);
        if (reps.isEmpty()) return;
        String askerName = userName(askerId);
        boolean dialogue = reps.size() == 1 && optIn(askerId);
        List<Map<String, Object>> turns = new ArrayList<>();
        markAgentBusy(t.id);
        try {
            for (ParticipantAgent rep : reps) {
                try {
                    talk(cc, t, askerId, dialogue, question, rep, askerName, turns);
                } catch (Exception ignored) {
                    // one failing agent must not block the others
                }
            }
        } finally {
            clearAgentBusy(t.id);
        }
        if (!turns.isEmpty()) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("threadId", t.id.toString());
            a.put("auto", true);
            a.put("question", question);
            a.put("turns", turns);
            audit.record("CONTRACT", c.id.toString(), "AGENT_CHANNEL_MESSAGE", askerId, null, a);
        }
    }

    /** One ask→reply run, optionally continued by the asker's own agent (clarify/reply back). */
    private void talk(Contract c, CommentThread t, UUID me, boolean dialogue, String question,
                      ParticipantAgent rep, String askerName, List<Map<String, Object>> turns) {
        UUID threadContractId = c.id;
        String pendingQuestion = question;
        int targetReplies = 0;
        int askerReplies = 0;
        while (true) {
            // the agent speaks FOR rep: ground it in what REP can see (their access), not the asker's
            String context = contractContext(c, rep.userId) + relatedContracts(c, rep, pendingQuestion) + threadHistory(t);
            // the asker's agent must never receive records from beyond the asker's own access
            String clarifyContext = contractContext(c, me) + threadHistory(t);
            AiService.AgentReply reply = ai.agentDiscussion(threadContractId, rep.userId, rep.name, rep.roleHint,
                    pendingQuestion, context);
            CommentMessage m = new CommentMessage();
            m.threadId = t.id;
            m.authorUserId = null;
            m.channel = "AGENT";
            m.authorAgent = rep.name;
            m.representedUserId = rep.userId;
            m.bodyHtml = htmlOf(reply.reply());
            messages.save(m);
            targetReplies++;
            turns.add(turnInfo(rep.userId, rep.name));
            if (!dialogue || targetReplies >= 2 || askerReplies >= 1) return;

            try {
                AiService.AgentClarify cl = ai.agentClarify(threadContractId, me, askerName,
                        rep.name, rep.roleHint, question, reply.reply(), clarifyContext);
                boolean hasComment = !cl.comment().isBlank();
                boolean hasQuestion = !cl.questionForOther().isBlank();
                if (!hasComment && !hasQuestion) return;

                List<String> parts = new ArrayList<>();
                if (hasComment) parts.add(cl.comment());
                if (hasQuestion) parts.add("Question for " + rep.name + ":\n" + cl.questionForOther());
                CommentMessage am = new CommentMessage();
                am.threadId = t.id;
                am.authorUserId = null;
                am.channel = "AGENT";
                am.authorAgent = askerName;
                am.representedUserId = me;
                am.bodyHtml = htmlOf(String.join("\n\n", parts));
                messages.save(am);
                askerReplies++;
                turns.add(turnInfo(me, askerName));
                if (!hasQuestion) return;
                pendingQuestion = AiService.neutralizeFences(cl.questionForOther());
            } catch (Exception e) {
                return; // asker's agent stumbled — the human can still continue the thread
            }
        }
    }

    private Map<String, Object> turnInfo(UUID userId, String name) {
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("representedUserId", userId);
        rec.put("representedName", name);
        return rec;
    }

    private static final Set<String> SEARCH_STOPWORDS = Set.of(
            "contract", "contracts", "record", "thread", "question", "answer", "about",
            "would", "could", "should", "there", "their", "where", "these", "those",
            "other", "before", "after", "because", "being", "under", "again", "without",
            "whether", "between", "notice", "terms", "clause", "agreement", "which",
            "anything", "something", "information", "provide", "explain", "confirm", "based");

    /**
     * Search over everything THIS representative can view: contracts sharing the current one's
     * counterparty, plus keyword hits from the question (each keyword searched strictly within
     * the representative's visible set, via listFiltered's access filtering). Rendered as a
     * compact index so an agent like Ivy's can answer from Ivy's whole corpus, not just the
     * current contract. Best-effort: on any failure returns "" and the answer grounds in the
     * current record alone.
     */
    private String relatedContracts(Contract c, ParticipantAgent rep, String question) {
        List<Map<String, Object>> found = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        try {
            Map<String, Object> base = contractService.get(c.id, rep.userId);
            firstPartyName(base).ifPresent(p ->
                    collect(found, seen, contractService.listFiltered(rep.userId, Map.of("counterparty_name", p)), c.id));
            if (question != null) {
                int tokens = 0;
                for (String tok : question.toLowerCase().split("[^a-z0-9]+")) {
                    if (tokens >= 4 || found.size() > 8) break;
                    if (tok.length() < 5 || SEARCH_STOPWORDS.contains(tok)) continue;
                    tokens++;
                    collect(found, seen, contractService.listFiltered(rep.userId, Map.of("keyword", tok)), c.id);
                }
            }
        } catch (Exception e) {
            return "";
        }
        if (found.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\nRELATED CONTRACTS (searched across every contract ")
                .append(rep.name).append(" can access — matches for the question's counterparties/keywords):\n");
        int shown = 0;
        for (Map<String, Object> m : found) {
            if (shown >= 5) break;
            sb.append("- ").append(m.get("contractNumber")).append(" — ").append(m.get("title"))
                    .append(" — status ").append(m.get("status"));
            Object cps = m.get("counterparties");
            if (cps instanceof List<?> cl && !cl.isEmpty()) sb.append(" — counterparty ").append(cl.get(0));
            if (m.get("valueAmount") != null) sb.append(" — value ").append(m.get("valueAmount")).append(" ").append(m.get("currency"));
            if (m.get("expiryDate") != null) sb.append(" — expiry ").append(m.get("expiryDate"));
            Object sum = m.get("summary");
            if (sum != null && !String.valueOf(sum).isBlank()) {
                String s = String.valueOf(sum);
                sb.append(" — ").append(s.length() > 160 ? s.substring(0, 160) + "…" : s);
            }
            sb.append("\n");
            shown++;
        }
        return sb.toString();
    }

    private void collect(List<Map<String, Object>> found, Set<String> seen,
                         List<Map<String, Object>> results, UUID excludeId) {
        for (Map<String, Object> m : results) {
            UUID id = m.get("id") instanceof UUID u ? u : null;
            if (id == null || id.equals(excludeId) || seen.contains(id.toString())) continue;
            seen.add(id.toString());
            found.add(m);
        }
    }

    private static Optional<String> firstPartyName(Map<String, Object> base) {
        if (base.get("parties") instanceof List<?> ps && !ps.isEmpty()) {
            Object p = ps.get(0);
            if (p instanceof Map<?, ?> pm) {
                Object legalName = pm.get("legalName");
                if (legalName != null && !String.valueOf(legalName).isBlank()) return Optional.of(String.valueOf(legalName));
            }
        }
        return Optional.empty();
    }

    /** A participant whose personal agent may answer in the thread. spec: domains they can speak to. */
    private record ParticipantAgent(UUID userId, String name, String roleHint, Set<String> spec, int order) {}

    /**
     * Routing rule: each participant has their own agent; the question goes to the participant(s)
     * best placed to answer. Domains (FINANCIAL/LEGAL/APPROVAL/GENERAL) are matched against the
     * participant's platform + contract roles — a specialist beats the owner for their domain,
     * and the owner is the default voice for general questions. If the question spans two domains,
     * a second, different participant's agent contributes as well. Only participants who can view
     * the contract AND opted in are eligible.
     */
    private List<ParticipantAgent> representatives(Contract c, String question) {
        return representatives(c, question, null);
    }

    /** Same routing, but the asker's own agent is excluded — the person who raised the point
     *  doesn't answer it; another participant's agent does. */
    private List<ParticipantAgent> representatives(Contract c, String question, UUID excludeUserId) {
        List<ParticipantAgent> pool = candidatePool(c);
        if (excludeUserId != null) pool.removeIf(a -> a.userId.equals(excludeUserId));
        String q = question.toLowerCase();
        List<String> domains = new ArrayList<>();
        if (q.matches(".*(\\bpayment\\b|\\binvoice\\b|\\bfee\\b|\\bfees\\b|\\bvalue\\b|\\bcost\\b|\\bpricing\\b|\\brate\\b|\\bbudget\\b).*")) domains.add("FINANCIAL");
        if (q.matches(".*(\\bliabilit\\b|\\bindemn\\b|\\bterm\\b|\\bterms\\b|\\bnotice\\b|\\brenew\\b|\\bexpiry\\b|\\bexpire\\b|\\bterminat\\b|\\bclause\\b|\\brisk\\b|\\bconfidential\\b|\\bgovern\\b|\\bcomplian\\b).*")) domains.add("LEGAL");
        if (q.matches(".*(\\bapprov\\w*|\\bworkflow\\b|\\bstatus\\b|\\bstage\\b|\\bsign\\b|\\bwhen will\\b|\\bdelay\\b|\\bpending\\b|\\bprogress\\b).*")) domains.add("APPROVAL");
        if (domains.isEmpty() || q.matches(".*(owner|who manages|responsible).*")) domains.add("GENERAL");

        pool.sort(Comparator.comparingInt(a -> a.order));

        List<ParticipantAgent> chosen = new ArrayList<>();
        for (String domain : domains.subList(0, Math.min(2, domains.size()))) {
            ParticipantAgent best = pool.stream()
                    .filter(cand -> !chosen.stream().anyMatch(x -> x.userId.equals(cand.userId)))
                    .min(Comparator.comparingInt((ParticipantAgent cand) -> fit(cand, c, domain))
                            .thenComparingInt(cand -> cand.order))
                    .orElse(null);
            if (best != null && fit(best, c, domain) < 100) chosen.add(best);
        }
        if (chosen.isEmpty() && !pool.isEmpty())
            chosen.add(pool.stream().min(Comparator.comparingInt(a -> a.order)).get());
        return chosen;
    }

    /** Everyone whose personal agent may speak on this contract: owner, assigned lawyer, participants —
     *  filtered to users who can view the contract AND opted in. */
    private List<ParticipantAgent> candidatePool(Contract c) {
        List<ParticipantAgent> pool = new ArrayList<>();
        addCandidate(pool, c, c.ownerUserId, null, 0);
        if (c.assignedLawyerId != null && !c.assignedLawyerId.equals(c.ownerUserId))
            addCandidate(pool, c, c.assignedLawyerId, null, 1);
        int order = 2;
        for (com.acme.clm.domain.ContractParticipant p : participants.findByContractId(c.id)) {
            if (p.userId.equals(c.ownerUserId) || p.userId.equals(c.assignedLawyerId)) continue;
            addCandidate(pool, c, p.userId, p.role, order++);
        }
        return pool;
    }

    /** Fit score for a candidate on a question domain; 100+ means not a fit. */
    private static int fit(ParticipantAgent cand, Contract c, String domain) {
        boolean owner = c.ownerUserId != null && c.ownerUserId.equals(cand.userId);
        if ("GENERAL".equals(domain)) return owner ? 0 : 5;
        boolean specialist = cand.spec.contains(domain);
        if (specialist) return owner ? 3 : 1;
        return owner ? 3 : 100;
    }

    private void addCandidate(List<ParticipantAgent> pool, Contract c, UUID userId, String participantRole, int order) {
        if (userId == null) return;
        AppUser u = users.findById(userId).orElse(null);
        if (u == null || !access.canView(u.id, c) || !optIn(u.id)) return;
        boolean owner = userId.equals(c.ownerUserId);
        boolean lawyer = userId.equals(c.assignedLawyerId);
        String roleHint = owner ? "contract owner" : lawyer ? "assigned lawyer"
                : participantRole == null ? "participant"
                : participantRole.toLowerCase() + " participant";
        String name = u.displayName == null || u.displayName.isBlank() ? "participant" : u.displayName;
        if (name.length() > 64) name = name.substring(0, 63) + "…";

        // domains this participant can speak to, from platform roles + contract role
        Set<String> spec = new HashSet<>();
        for (String r : (u.roles == null ? "" : u.roles).split(",")) {
            switch (r.trim().toUpperCase()) {
                case "FINANCE" -> spec.add("FINANCIAL");
                case "LEGAL", "GENERAL_COUNSEL", "PARALEGAL" -> spec.add("LEGAL");
                case "APPROVER" -> spec.add("APPROVAL");
                case "ADMIN" -> { spec.add("LEGAL"); spec.add("APPROVAL"); spec.add("FINANCIAL"); }
                default -> { }
            }
        }
        if ("APPROVER".equalsIgnoreCase(participantRole)) spec.add("APPROVAL");
        if ("CONTRIBUTOR".equalsIgnoreCase(participantRole)) { spec.add("LEGAL"); spec.add("APPROVAL"); spec.add("FINANCIAL"); }
        pool.add(new ParticipantAgent(u.id, name, roleHint, spec, order));
    }

    private String htmlOf(String reply) {
        StringBuilder html = new StringBuilder();
        for (String para : reply.split("\\n\\n")) {
            html.append("<p>").append(toHtml(para)).append("</p>");
        }
        return html.toString();
    }

    // ----------------------------------------------------- helpers

    private Contract loadVisible(UUID contractId) {
        Contract c = contracts.findById(contractId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Contract not found"));
        if (!access.canView(current.id(), c))
            throw new ApiExceptions.NotFoundException("Contract not found");
        return c;
    }

    private boolean optIn(UUID userId) {
        return settings.findById(new UserSetting.Key(userId, MeController.AGENT_OPTIN_KEY))
                .map(s -> Boolean.TRUE.equals(com.acme.clm.common.Json.readMap(s.value).get("enabled")))
                .orElse(false);
    }

    private String contractContext(Contract c, UUID me) {
        Map<String, Object> cm = contractService.get(c.id, me);
        StringBuilder ctx = new StringBuilder();
        ctx.append("Contract ").append(cm.get("contractNumber")).append(" — ").append(cm.get("title")).append("\n");
        line(ctx, cm, "type"); line(ctx, cm, "status"); line(ctx, cm, "governingLaw");
        if (cm.get("valueAmount") != null) ctx.append("Value: ").append(cm.get("valueAmount")).append(" ").append(cm.get("currency")).append("\n");
        line(ctx, cm, "effectiveDate"); line(ctx, cm, "expiryDate");
        if (cm.get("expiryDate") != null) {
            ctx.append("Auto-renew: ").append(Boolean.TRUE.equals(cm.get("autoRenew")) ? "yes" : "no").append("\n");
        }
        Object summary = cm.get("summary");
        if (summary != null && !String.valueOf(summary).isBlank()) {
            String s = String.valueOf(summary);
            ctx.append("Summary: ").append(s.length() > 1200 ? s.substring(0, 1200) : s).append("\n");
        }
        if (cm.get("terms") instanceof List<?> terms && !terms.isEmpty()) {
            ctx.append("Extracted terms:\n");
            int shown = 0;
            for (Object o : terms) {
                if (shown >= 25) break;
                if (o instanceof Map<?, ?> tm) {
                    String value = String.valueOf(tm.get("value"));
                    if (value.length() > 200) value = value.substring(0, 200) + "…";
                    ctx.append("- ").append(tm.get("key")).append(" = ").append(value)
                            .append(Boolean.TRUE.equals(tm.get("verified")) ? " (verified)" : "").append("\n");
                    shown++;
                }
            }
        }
        return ctx.toString();
    }

    private static void line(StringBuilder ctx, Map<String, Object> cm, String key) {
        Object v = cm.get(key);
        if (v != null && !String.valueOf(v).isBlank()) ctx.append(cap(key)).append(": ").append(v).append("\n");
    }

    private static String cap(String key) {
        return Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Escaped reply → very small markdown subset (bold, bullets, model-emitted <br/>). Safe by construction. */
    private static String toHtml(String para) {
        String s = escape(para);
        s = s.replaceAll("(?m)^[ \\t]*-[ \\t]", "• ");
        s = s.replace("\n", "<br/>");
        s = s.replace("&lt;br&gt;", "<br/>").replace("&lt;br/&gt;", "<br/>");
        s = s.replaceAll("\\*\\*([^*\\n]+)\\*\\*", "<strong>$1</strong>");
        return s;
    }

    /** Recent thread messages so follow-up questions can reference the ongoing discussion. */
    private String threadHistory(CommentThread t) {
        List<CommentMessage> msgs = messages.findByThreadIdOrderByCreatedAtAsc(t.id);
        if (msgs.size() <= 1) return "";
        StringBuilder sb = new StringBuilder("\nTHREAD HISTORY (oldest first):\n");
        int from = Math.max(0, msgs.size() - 16);
        for (CommentMessage m : msgs.subList(from, msgs.size())) {
            if (m.deleted) continue;
            String who = "AGENT".equals(m.channel) ? (m.authorAgent == null ? "agent" : m.authorAgent)
                    : userName(m.authorUserId);
            String body = sanitizer.plainText(m.bodyHtml == null ? "" : m.bodyHtml);
            if (body.length() > 400) body = body.substring(0, 400) + "…";
            body = AiService.neutralizeFences(body);
            sb.append("- ").append(who).append(": ").append(body).append("\n");
        }
        return sb.toString();
    }

    private String userName(UUID id) {
        return id == null ? "unknown" : users.findById(id).map(u -> u.displayName).orElse("unknown");
    }
}