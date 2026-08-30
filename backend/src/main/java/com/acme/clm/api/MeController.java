package com.acme.clm.api;

import com.acme.clm.config.CurrentUser;
import com.acme.clm.domain.*;
import com.acme.clm.repo.Repos;
import com.acme.clm.service.AccessService;
import com.acme.clm.service.WorkflowService;
import java.util.*;
import org.springframework.web.bind.annotation.*;

/** "What needs my attention" — powers the dashboard callouts and the sidebar badges. */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final Repos.Contracts contracts;
    private final Repos.CommentThreads threads;
    private final Repos.CommentMessages messages;
    private final Repos.AccessRequests accessRequests;
    private final Repos.ContractParticipants participants;
    private final Repos.UserSettings settings;
    private final WorkflowService workflow;
    private final AccessService access;
    private final CurrentUser current;

    public MeController(Repos.Contracts contracts, Repos.CommentThreads threads, Repos.CommentMessages messages,
                        Repos.AccessRequests accessRequests, Repos.ContractParticipants participants,
                        Repos.UserSettings settings, WorkflowService workflow, AccessService access,
                        CurrentUser current) {
        this.contracts = contracts;
        this.threads = threads;
        this.messages = messages;
        this.accessRequests = accessRequests;
        this.participants = participants;
        this.settings = settings;
        this.workflow = workflow;
        this.access = access;
        this.current = current;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        UUID me = current.id();

        List<Map<String, Object>> tasks = workflow.myTasks(me);

        // contracts I'm involved in (owner/creator/lawyer/participant)
        Set<UUID> mine = new HashSet<>();
        for (Contract c : contracts.findAll()) {
            if (me.equals(c.ownerUserId) || me.equals(c.createdBy) || me.equals(c.assignedLawyerId)) mine.add(c.id);
        }
        participants.findByUserId(me).forEach(p -> mine.add(p.contractId));
        tasks.forEach(t -> { Object cid = t.get("contractId"); if (cid != null) mine.add(UUID.fromString(String.valueOf(cid))); });

        // open discussions on those contracts where I'm not the last to speak
        List<Map<String, Object>> discussions = new ArrayList<>();
        for (UUID cid : mine) {
            for (CommentThread th : threads.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("CONTRACT", cid.toString())) {
                if (!"OPEN".equals(th.status)) continue;
                var msgs = messages.findByThreadIdOrderByCreatedAtAsc(th.id);
                if (msgs.isEmpty()) continue;
                boolean iAmLast = msgs.get(msgs.size() - 1).authorUserId.equals(me);
                boolean iParticipated = msgs.stream().anyMatch(m -> m.authorUserId.equals(me));
                Contract c = contracts.findById(cid).orElse(null);
                Map<String, Object> dm = new LinkedHashMap<>();
                dm.put("threadId", th.id);
                dm.put("title", th.title);
                dm.put("contractId", cid);
                dm.put("contractNumber", c == null ? null : c.contractNumber);
                dm.put("messages", msgs.size());
                dm.put("awaitingMe", !iAmLast && (iParticipated || me.equals(c == null ? null : c.ownerUserId)));
                discussions.add(dm);
            }
        }
        long discussionsAwaiting = discussions.stream().filter(d -> Boolean.TRUE.equals(d.get("awaitingMe"))).count();

        // access requests I can decide
        List<Map<String, Object>> toDecide = new ArrayList<>();
        for (AccessRequest r : accessRequests.findByStatusOrderByCreatedAtDesc("PENDING")) {
            if (r.userId.equals(me)) continue;
            if (access.canApprove(me, access.constraintsOf(r.constraints))) {
                Map<String, Object> rm = new LinkedHashMap<>();
                rm.put("id", r.id);
                rm.put("scope", access.humanScope(access.constraintsOf(r.constraints)));
                rm.put("createdAt", r.createdAt);
                toDecide.add(rm);
            }
        }

        // my drafts still to submit
        List<Map<String, Object>> myDrafts = contracts.findAll().stream()
                .filter(c -> me.equals(c.ownerUserId) && "DRAFT".equals(c.status))
                .map(c -> Map.<String, Object>of("id", c.id, "contractNumber", c.contractNumber, "title", c.title))
                .toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("openTasks", tasks);
        out.put("openTaskCount", tasks.size());
        out.put("discussions", discussions);
        out.put("discussionsAwaiting", discussionsAwaiting);
        out.put("accessToDecide", toDecide);
        out.put("accessToDecideCount", toDecide.size());
        out.put("myDrafts", myDrafts);
        out.put("attentionCount", tasks.size() + discussionsAwaiting + toDecide.size() + myDrafts.size());
        return out;
    }

    /** Recent contracts the current user can access — the intake start screen list. */
    @GetMapping("/recent-contracts")
    public List<Map<String, Object>> recentContracts(@RequestParam(defaultValue = "8") int limit) {
        UUID me = current.id();
        List<Contract> visible = access.filterVisible(me, contracts.findAll());
        return visible.stream()
                .sorted(Comparator.comparing((Contract c) -> c.updatedAt == null ? c.createdAt : c.updatedAt).reversed())
                .limit(limit)
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.id);
                    m.put("contractNumber", c.contractNumber);
                    m.put("title", c.title);
                    m.put("type", c.contractTypeCode);
                    m.put("status", c.status);
                    m.put("summary", c.summary);
                    m.put("mine", me.equals(c.ownerUserId) || me.equals(c.createdBy));
                    return m;
                })
                .toList();
    }

    /** The contract types this user personally creates most often — intake quick links. */
    @GetMapping("/top-contract-types")
    public List<Map<String, Object>> topContractTypes(@RequestParam(defaultValue = "3") int limit) {
        UUID me = current.id();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Contract c : contracts.findAll()) {
            if (!me.equals(c.createdBy) || c.contractTypeCode == null) continue;
            counts.merge(c.contractTypeCode, 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .filter(e -> e.getValue() >= 2)
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(e -> Map.<String, Object>of("type", e.getKey(), "count", e.getValue()))
                .toList();
    }

    // ----------------------------------------------------- dashboard config (layout + custom charts)

    private static final String DASHBOARD_KEY = "dashboard";
    private static final List<String> DEFAULT_LAYOUT =
            List.of("ai-insight", "attention", "stats", "by-type", "expiry", "risk", "migration", "inquiry", "tasks");

    @GetMapping("/dashboard-config")
    public Map<String, Object> dashboardConfig() {
        return readDashboard(current.id());
    }

    /** Replaces the whole dashboard config (section order/visibility + custom chart sections). */
    @PutMapping("/dashboard-config")
    public Map<String, Object> saveDashboardConfig(@RequestBody Map<String, Object> body) {
        UUID me = current.id();
        Map<String, Object> clean = new LinkedHashMap<>();
        Object layout = body.get("layout");
        if (layout instanceof List<?> l) {
            clean.put("layout", l.stream()
                    .filter(x -> x instanceof Map<?, ?> m && m.get("key") != null)
                    .map(x -> {
                        Map<?, ?> m = (Map<?, ?>) x;
                        Map<String, Object> e = new LinkedHashMap<>();
                        e.put("key", String.valueOf(m.get("key")));
                        e.put("visible", !Boolean.FALSE.equals(m.get("visible")));
                        e.put("size", "half".equals(m.get("size")) ? "half" : "full");
                        return e;
                    })
                    .toList());
        }
        Object charts = body.get("charts");
        if (charts instanceof List<?> l) {
            clean.put("charts", l.stream()
                    .filter(x -> x instanceof Map<?, ?> m && m.get("id") != null && m.get("question") != null)
                    .toList());
        }
        UserSetting s = settings.findById(new UserSetting.Key(me, DASHBOARD_KEY)).orElseGet(UserSetting::new);
        s.userId = me;
        s.prefKey = DASHBOARD_KEY;
        s.value = com.acme.clm.common.Json.write(clean);
        s.updatedAt = java.time.Instant.now();
        settings.save(s);
        return readDashboard(me);
    }

    private Map<String, Object> readDashboard(UUID userId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("layout", DEFAULT_LAYOUT.stream().map(k -> Map.of("key", k, "visible", true)).toList());
        out.put("charts", List.of());
        settings.findById(new UserSetting.Key(userId, DASHBOARD_KEY)).ifPresent(s -> {
            Map<String, Object> stored = com.acme.clm.common.Json.readMap(s.value);
            if (stored.get("layout") instanceof List<?> l) out.put("layout", l);
            if (stored.get("charts") instanceof List<?> l) out.put("charts", l);
        });
        return out;
    }
}
