package com.acme.clm.service;

import com.acme.clm.common.ApiExceptions;
import com.acme.clm.common.Json;
import com.acme.clm.domain.*;
import com.acme.clm.repo.Repos;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Custom JSON-definition state-machine engine (plan §7). Role-based assignment, guards, SLA, audit per transition. */
@Service
public class WorkflowService {

    private final Repos.WorkflowDefinitions definitions;
    private final Repos.WorkflowInstances instances;
    private final Repos.WorkflowTasks tasks;
    private final Repos.Contracts contracts;
    private final Repos.ContractTypes types;
    private final Repos.Users users;
    private final Repos.LegalEntities entities;
    private final Repos.SigningAuthorities signingAuthorities;
    private final Repos.ClauseVariantUsages clauseUsages;
    private final Repos.ClauseVariants clauseVariants;
    private final Repos.PrecedentLinks precedents;
    private final AutoRejectService autoReject;
    private final AuditService audit;

    public WorkflowService(Repos.WorkflowDefinitions definitions, Repos.WorkflowInstances instances,
                           Repos.WorkflowTasks tasks, Repos.Contracts contracts, Repos.ContractTypes types,
                           Repos.Users users, Repos.LegalEntities entities, Repos.SigningAuthorities signingAuthorities,
                           Repos.ClauseVariantUsages clauseUsages, Repos.ClauseVariants clauseVariants,
                           Repos.PrecedentLinks precedents, AutoRejectService autoReject, AuditService audit) {
        this.definitions = definitions;
        this.instances = instances;
        this.tasks = tasks;
        this.contracts = contracts;
        this.types = types;
        this.users = users;
        this.entities = entities;
        this.signingAuthorities = signingAuthorities;
        this.clauseUsages = clauseUsages;
        this.clauseVariants = clauseVariants;
        this.precedents = precedents;
        this.autoReject = autoReject;
        this.audit = audit;
    }

    @Transactional
    public WorkflowInstance start(UUID contractId, UUID actor) {
        Contract c = contracts.findById(contractId).orElseThrow(() -> new ApiExceptions.NotFoundException("Contract not found"));
        requireRequestor(c, actor, "submit for approval");
        Optional<WorkflowInstance> running = instances.findByContractId(contractId).stream()
                .filter(x -> "RUNNING".equals(x.status)).findFirst();
        if (running.isPresent()) {
            return running.get();
        }
        ContractTypeDefinition def = types.findById(c.contractTypeCode).orElseThrow();
        WorkflowDefinition wf = def.defaultWorkflowId != null
                ? definitions.findById(def.defaultWorkflowId).orElseThrow()
                : definitions.findAll().stream().findFirst().orElseThrow();
        JsonNode d = Json.read(wf.definition);
        String initial = d.path("initial").asText(d.path("states").path(0).path("key").asText("start"));

        WorkflowInstance wi = new WorkflowInstance();
        wi.contractId = contractId;
        wi.workflowDefinitionId = wf.id;
        wi.workflowKey = wf.key;
        wi.workflowVersionNo = wf.versionNo;
        wi.currentState = initial;
        wi.status = "RUNNING";
        instances.save(wi);

        c.status = "IN_REVIEW";
        contracts.save(c);

        enterState(wi, stateNode(d, initial), actor);
        audit.record("WORKFLOW", wi.id.toString(), "STARTED", actor, null,
                Map.of("workflow", wf.key, "state", initial, "contract", c.contractNumber));
        return wi;
    }

    @Transactional
    public Map<String, Object> act(UUID taskId, String event, String comment, UUID actor) {
        WorkflowTask task = tasks.findById(taskId).orElseThrow(() -> new ApiExceptions.NotFoundException("Task not found"));
        if (!"OPEN".equals(task.status)) throw new ApiExceptions.BadRequestException("Task is not open");
        WorkflowInstance wi = instances.findById(task.workflowInstanceId).orElseThrow();
        Contract contract = contracts.findById(wi.contractId).orElseThrow();
        // Revision events (resubmit a recalled/rejected draft) belong to the requestor only.
        if ("REVISION".equals(task.taskType)) {
            requireRequestor(contract, actor, "resubmit");
        }
        WorkflowDefinition wf = definitions.findById(wi.workflowDefinitionId).orElseThrow();
        JsonNode d = Json.read(wf.definition);
        JsonNode state = stateNode(d, wi.currentState);

        JsonNode transition = null;
        for (JsonNode tr : state.path("transitions")) {
            if (tr.path("on").asText().equals(event)) { transition = tr; break; }
        }
        if (transition == null) {
            throw new ApiExceptions.BadRequestException("Event '" + event + "' is not valid from state '" + wi.currentState + "'");
        }
        String target = transition.path("to").asText();
        JsonNode targetState = stateNode(d, target);

        // guards on the target state
        List<String> failed = evaluateGuards(targetState, wi.contractId, actor);
        if (!failed.isEmpty()) {
            throw new ApiExceptions.BadRequestException("Blocked by guard(s): " + String.join("; ", failed));
        }

        task.status = "DONE";
        task.outcome = event;
        task.comments = comment;
        task.completedAt = Instant.now();
        tasks.save(task);

        wi.currentState = target;
        instances.save(wi);
        audit.record("WORKFLOW", wi.id.toString(), "TRANSITION", actor,
                Map.of("from", state.path("key").asText(), "event", event),
                Map.of("to", target));

        Contract c = contracts.findById(wi.contractId).orElseThrow();
        if ("end".equals(targetState.path("type").asText()) || target.startsWith("closed") || "executed".equals(target)) {
            wi.status = "COMPLETED";
            wi.completedAt = Instant.now();
            instances.save(wi);
            c.status = target.contains("reject") ? "CLOSED_REJECTED" : "EXECUTED";
            if ("EXECUTED".equals(c.status)) c.effectiveDate = c.effectiveDate == null ? java.time.LocalDate.now() : c.effectiveDate;
            contracts.save(c);
            audit.record("CONTRACT", c.id.toString(), c.status, actor, null, Map.of("via", "workflow"));
        } else {
            enterState(wi, targetState, actor);
        }

        return status(wi.contractId);
    }

    private void enterState(WorkflowInstance wi, JsonNode state, UUID actor) {
        if (!"task".equals(state.path("type").asText())) return;
        Contract c = contracts.findById(wi.contractId).orElseThrow();
        int slaHours = state.path("slaHours").asInt(48);
        WorkflowTask t = new WorkflowTask();
        t.workflowInstanceId = wi.id;
        t.stateKey = state.path("key").asText();
        t.taskType = state.path("taskType").asText("APPROVAL");
        String role = state.path("assignment").path("role").asText("owner");
        t.assignedRoleExpression = role;
        t.assignedUserId = resolveRole(role, c);
        t.status = "OPEN";
        t.dueAt = Instant.now().plus(slaHours, ChronoUnit.HOURS);
        tasks.save(t);

        wi.slaDueAt = t.dueAt;
        instances.save(wi);
        audit.record("WORKFLOW_TASK", t.id.toString(), "ASSIGNED", actor, null,
                Map.of("state", t.stateKey, "role", role,
                        "assignee", t.assignedUserId == null ? "unassigned" : t.assignedUserId.toString()));
        maybeAutoReject(wi, t);
    }

    /**
     * Auto-rejection (approver rules): when a new review/approval task lands on a user who owns
     * enabled rejection rules scoped to this contract, and the rule's requirements are unmet, the
     * request is rejected automatically on that approver's behalf — deterministic, no LLM involved.
     * Owners whose global trigger is DELAYED are skipped here; {@link #rejectDueTasks()} evaluates
     * them once the configured delay has passed.
     */
    private void maybeAutoReject(WorkflowInstance wi, WorkflowTask t) {
        // REVIEW and APPROVAL tasks are human gate-steps a rule owner can pre-decide; REVISION
        // belongs to the requestor (resubmission) and must never auto-reject.
        if ("REVISION".equals(t.taskType) || t.assignedUserId == null) return;
        if (autoReject.settingFor(t.assignedUserId).mode == AutoRejectSetting.Mode.DELAYED) return;
        Contract c = contracts.findById(wi.contractId).orElse(null);
        if (c == null) return;
        applyAutoReject(wi, t, c);
    }

    /** Shared rejection application for the on-assignment hook and the delayed sweep. */
    private void applyAutoReject(WorkflowInstance wi, WorkflowTask t, Contract c) {
        Optional<AutoRejectService.Decision> d = autoReject.rejectionFor(c, t.taskType, t.assignedUserId);
        if (d.isEmpty()) return;

        // the current state must have a way out to a rejected end state; otherwise skip
        WorkflowDefinition wf = definitions.findById(wi.workflowDefinitionId).orElse(null);
        if (wf == null) return;
        JsonNode stateNode = stateNode(Json.read(wf.definition), wi.currentState);
        String rejectTarget = null;
        for (JsonNode tr : stateNode.path("transitions")) {
            String to = tr.path("to").asText();
            if ("reject".equals(tr.path("on").asText())) { rejectTarget = to; break; }
            if (to.contains("reject") && rejectTarget == null) rejectTarget = to;
        }
        if (rejectTarget == null) return;

        AutoRejectService.Decision dec = autoReject.applyFired(d.get());
        AutoRejectService.Eval ev = dec.eval();
        String reason = "Rule \"" + dec.rule().name + "\": required " + String.join("; ", ev.unmet());

        t.status = "DONE";
        t.outcome = "AUTO_REJECT";
        t.comments = reason.length() > 1000 ? reason.substring(0, 1000) : reason;
        t.completedAt = Instant.now();
        tasks.save(t);

        wi.currentState = rejectTarget;
        wi.status = "COMPLETED";
        wi.completedAt = Instant.now();
        instances.save(wi);

        c.status = "CLOSED_REJECTED";
        contracts.save(c);

        audit.record("WORKFLOW_TASK", t.id.toString(), "AUTO_REJECT", null, null,
                Map.of("rule", dec.rule().name, "ruleId", dec.rule().id.toString(),
                        "contractNumber", c.contractNumber, "reason", reason));
        audit.record("AUTO_REJECT_RULE", dec.rule().id.toString(), "FIRED", null, null,
                Map.of("contractId", c.id.toString(), "contractNumber", c.contractNumber,
                        "state", t.stateKey, "reason", reason));
        audit.record("CONTRACT", c.id.toString(), "CLOSED_REJECTED", null, null,
                Map.of("via", "auto_reject", "rule", dec.rule().name));
    }

    /**
     * Delayed-trigger sweep (called from the scheduler): evaluates auto-reject rules for open
     * review/approval tasks whose assignee's global trigger is DELAYED, once the configured
     * number of hours has passed since the task was created. Returns rejections applied.
     */
    public int rejectDueTasks() {
        int fired = 0;
        Instant now = Instant.now();
        for (WorkflowTask t : tasks.findByStatus("OPEN")) {
            if ("REVISION".equals(t.taskType) || t.assignedUserId == null) continue;
            AutoRejectSetting s = autoReject.settingFor(t.assignedUserId);
            if (s.mode != AutoRejectSetting.Mode.DELAYED || s.delayHours <= 0) continue;
            // Prospective only: the delay clock runs from when the setting was last changed, so
            // enabling a delay never retroactively rejects tasks already sitting in the queue.
            if (t.createdAt.isBefore(s.updatedAt)) continue;
            if (t.createdAt.plus(s.delayHours, ChronoUnit.HOURS).isAfter(now)) continue;
            WorkflowInstance wi = instances.findById(t.workflowInstanceId).orElse(null);
            if (wi == null || !"RUNNING".equals(wi.status)) continue;
            Contract c = contracts.findById(wi.contractId).orElse(null);
            if (c == null) continue;
            applyAutoReject(wi, t, c);
            fired++;
        }
        return fired;
    }

    private UUID resolveRole(String role, Contract c) {
        return switch (role) {
            case "owner", "signatory" -> c.ownerUserId != null ? c.ownerUserId : anyGc();
            case "owner_manager" -> users.findById(Optional.ofNullable(c.ownerUserId).orElse(anyGc()))
                    .map(u -> u.managerUserId).orElse(anyGc());
            case "legal_team" -> c.assignedLawyerId != null ? c.assignedLawyerId : anyByRole("LEGAL");
            case "legal_manager" -> anyGc();
            case "finance_approver" -> anyByRole("FINANCE");
            default -> c.ownerUserId != null ? c.ownerUserId : anyGc();
        };
    }

    private UUID anyByRole(String role) {
        return users.findAll().stream().filter(u -> u.roles != null && u.roles.contains(role))
                .map(u -> u.id).findFirst().orElseGet(this::anyGc);
    }

    private UUID anyGc() {
        return users.findAll().stream().filter(u -> u.roles != null && u.roles.contains("GENERAL_COUNSEL"))
                .map(u -> u.id).findFirst()
                .orElseGet(() -> users.findAll().stream().findFirst().map(u -> u.id).orElse(null));
    }

    private List<String> evaluateGuards(JsonNode state, UUID contractId, UUID actor) {
        List<String> failed = new ArrayList<>();
        for (JsonNode g : state.path("guards")) {
            String guard = g.asText();
            switch (guard) {
                case "signing_authority_valid" -> {
                    if (!signingAuthorityValid(contractId)) failed.add("no valid signing authority for the contract value");
                }
                case "no_open_deviations" -> {
                    if (hasOpenDeviations(contractId)) failed.add("unacknowledged clause deviations remain");
                }
                default -> { /* unknown guard: ignore */ }
            }
        }
        return failed;
    }

    public boolean signingAuthorityValid(UUID contractId) {
        Contract c = contracts.findById(contractId).orElseThrow();
        var auths = signingAuthorities.findByLegalEntityId(c.contractingEntityId);
        double value = c.valueAmount == null ? 0 : c.valueAmount.doubleValue();
        java.time.LocalDate today = java.time.LocalDate.now();
        return auths.stream().anyMatch(a ->
                (a.contractTypeCode == null || a.contractTypeCode.equals(c.contractTypeCode))
                && (a.maxValueAmount == null || a.maxValueAmount.doubleValue() >= value)
                && (a.validFrom == null || !today.isBefore(a.validFrom))
                && (a.validTo == null || !today.isAfter(a.validTo)));
    }

    public boolean hasOpenDeviations(UUID contractId) {
        boolean unacceptableClause = clauseUsages.findByContractId(contractId).stream().anyMatch(u ->
                clauseVariants.findById(u.clauseVariantId).map(v -> "UNACCEPTABLE".equals(v.positionTier)).orElse(false));
        boolean unackedCarried = precedents.findByContractId(contractId).stream().anyMatch(p ->
                p.deviationsCarried != null && !p.deviationsCarried.isBlank() && !"[]".equals(p.deviationsCarried.trim())
                        && p.acknowledgedBy == null);
        return unacceptableClause || unackedCarried;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> status(UUID contractId) {
        List<WorkflowInstance> wis = instances.findByContractId(contractId);
        if (wis.isEmpty()) return Map.of("started", false);
        WorkflowInstance wi = wis.stream().filter(x -> "RUNNING".equals(x.status)).findFirst()
                // several instances exist after recall/resubmit cycles: report on the most recent
                .orElseGet(() -> wis.stream().max(Comparator.comparing(x -> x.startedAt)).orElse(wis.get(0)));
        if ("CANCELLED".equals(wi.status)) return Map.of("started", false);
        List<Map<String, Object>> taskList = tasks.findByWorkflowInstanceId(wi.id).stream()
                .sorted(Comparator.comparing(t -> t.createdAt))
                .map(this::taskMap).toList();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("started", true);
        m.put("workflow", wi.workflowKey);
        m.put("currentState", wi.currentState);
        m.put("status", wi.status);
        m.put("slaDueAt", wi.slaDueAt);
        m.put("escalated", wi.isEscalated);
        m.put("availableEvents", availableEvents(wi));
        m.put("tasks", taskList);
        WorkflowDefinition wfDef = definitions.findById(wi.workflowDefinitionId).orElse(null);
        if (wfDef != null) {
            JsonNode d = Json.read(wfDef.definition);
            List<Map<String, Object>> states = new ArrayList<>();
            for (JsonNode s : d.path("states")) {
                states.add(Map.of("key", s.path("key").asText(), "type", s.path("type").asText("task")));
            }
            m.put("states", states);
        }
        return m;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> myTasks(UUID userId) {
        // newest task sent to me first, so the latest approval request is on top
        return tasks.findByAssignedUserIdAndStatus(userId, "OPEN").stream()
                .map(this::taskMapWithContract)
                .sorted(Comparator.comparing((Map<String, Object> m) -> (Instant) m.get("createdAt"),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> allOpenTasks() {
        return tasks.findByStatus("OPEN").stream().map(this::taskMapWithContract).toList();
    }

    /** Cancel a running workflow and its open tasks (used on recall-to-draft). */
    @Transactional
    public void cancelOpen(UUID contractId, UUID actor) {
        instances.findByContractId(contractId).stream()
                .filter(x -> "RUNNING".equals(x.status))
                .forEach(wi -> {
                    tasks.findByWorkflowInstanceId(wi.id).stream()
                            .filter(t -> "OPEN".equals(t.status))
                            .forEach(t -> { t.status = "CANCELLED"; t.completedAt = Instant.now(); tasks.save(t); });
                    wi.status = "CANCELLED";
                    wi.completedAt = Instant.now();
                    instances.save(wi);
                    audit.record("WORKFLOW", wi.id.toString(), "CANCELLED", actor, null, Map.of("contract", contractId.toString()));
                });
    }

    private void requireRequestor(Contract c, UUID actor, String action) {
        boolean requestor = actor != null
                && (actor.equals(c.ownerUserId) || actor.equals(c.createdBy));
        if (!requestor) {
            throw new ApiExceptions.ForbiddenException("Only the requestor can " + action + ".");
        }
    }

    private Map<String, Object> taskMap(WorkflowTask t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.id);
        m.put("state", t.stateKey);
        m.put("type", t.taskType);
        m.put("status", t.status);
        m.put("role", t.assignedRoleExpression);
        m.put("assignedUserId", t.assignedUserId);
        m.put("assignee", t.assignedUserId == null ? null
                : users.findById(t.assignedUserId).map(u -> u.displayName).orElse(null));
        m.put("dueAt", t.dueAt);
        m.put("createdAt", t.createdAt);
        m.put("outcome", t.outcome);
        m.put("comments", t.comments);
        m.put("overdue", t.dueAt != null && t.dueAt.isBefore(Instant.now()) && "OPEN".equals(t.status));
        return m;
    }

    private Map<String, Object> taskMapWithContract(WorkflowTask t) {
        Map<String, Object> m = new LinkedHashMap<>(taskMap(t));
        WorkflowInstance wi = instances.findById(t.workflowInstanceId).orElse(null);
        if (wi != null) {
            m.put("workflow", wi.workflowKey);
            m.put("currentState", wi.currentState);
            Contract c = contracts.findById(wi.contractId).orElse(null);
            if (c != null) {
                m.put("contractId", c.id);
                m.put("contractNumber", c.contractNumber);
                m.put("contractTitle", c.title);
                m.put("contractType", c.contractTypeCode);
                m.put("contractValue", c.valueAmount);
                m.put("currency", c.currency);
                m.put("availableEvents", availableEvents(wi));
            }
        }
        return m;
    }

    private List<String> availableEvents(WorkflowInstance wi) {
        WorkflowDefinition wf = definitions.findById(wi.workflowDefinitionId).orElse(null);
        if (wf == null) return List.of();
        JsonNode state = stateNode(Json.read(wf.definition), wi.currentState);
        List<String> events = new ArrayList<>();
        for (JsonNode tr : state.path("transitions")) events.add(tr.path("on").asText());
        return events;
    }

    private JsonNode stateNode(JsonNode def, String key) {
        for (JsonNode s : def.path("states")) {
            if (s.path("key").asText().equals(key)) return s;
        }
        throw new ApiExceptions.BadRequestException("Unknown workflow state: " + key);
    }

    /** SLA sweep (plan §7.3): escalate overdue tasks. */
    @Transactional
    public int escalateOverdue() {
        int escalated = 0;
        for (WorkflowTask t : tasks.findByStatus("OPEN")) {
            if (t.dueAt != null && t.dueAt.isBefore(Instant.now())) {
                WorkflowInstance wi = instances.findById(t.workflowInstanceId).orElse(null);
                if (wi != null && !wi.isEscalated) {
                    wi.isEscalated = true;
                    instances.save(wi);
                    t.assignedUserId = anyGc();
                    tasks.save(t);
                    audit.record("WORKFLOW_TASK", t.id.toString(), "ESCALATED", null, null,
                            Map.of("reason", "SLA breach", "reassignedTo", "legal_manager"));
                    escalated++;
                }
            }
        }
        return escalated;
    }
}
