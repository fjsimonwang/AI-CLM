package com.acme.clm.service;

import com.acme.clm.ai.AiService;
import com.acme.clm.common.ApiExceptions;
import com.acme.clm.common.Json;
import com.acme.clm.domain.AiReviewRun;
import com.acme.clm.domain.Contract;
import com.acme.clm.domain.ContractRisk;
import com.acme.clm.domain.ContractVersion;
import com.acme.clm.domain.Playbook;
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
 * Background AI document review: a run is persisted first (status RUNNING), then executed on a
 * worker pool, so the user can navigate away while it runs. Completed findings are also recorded
 * in the contract's risk register (source AI_REVIEW), deduplicated on the finding title.
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    private final Repos.ReviewRuns runs;
    private final Repos.Contracts contracts;
    private final Repos.ContractVersions versions;
    private final Repos.AiReviewRules reviewRules;
    private final Repos.Playbooks playbooks;
    private final Repos.ContractRisks risks;
    private final DraftingService drafting;
    private final AiService ai;

    public ReviewService(Repos.ReviewRuns runs, Repos.Contracts contracts, Repos.ContractVersions versions,
                         Repos.AiReviewRules reviewRules, Repos.Playbooks playbooks, Repos.ContractRisks risks,
                         DraftingService drafting, AiService ai) {
        this.runs = runs;
        this.contracts = contracts;
        this.versions = versions;
        this.reviewRules = reviewRules;
        this.playbooks = playbooks;
        this.risks = risks;
        this.drafting = drafting;
        this.ai = ai;
    }

    /** Validates there is something to review, persists the run, and kicks off execution. */
    public Map<String, Object> start(UUID contractId, UUID userId, List<UUID> playbookIds, List<String> ruleCodes) {
        Contract c = contracts.findById(contractId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Contract not found"));
        drafting.syncFromEditor(contractId, userId);
        ContractVersion latest = versions.findByContractIdOrderByVersionNoDesc(c.id).stream().findFirst().orElse(null);
        String body = latest == null ? null : latest.bodyText;
        if (body == null || body.isBlank())
            throw new ApiExceptions.BadRequestException("No assembled document text to review yet — assemble the document first.");

        AiReviewRun run = new AiReviewRun();
        run.contractId = contractId;
        run.requestedBy = userId;
        run.status = "RUNNING";
        runs.save(run);

        pool.submit(() -> {
            try {
                execute(run.id, userId, playbookIds, ruleCodes, body);
            } catch (Exception e) {
                log.warn("Background review {} failed: {}", run.id, e.toString());
            }
        });
        return view(run);
    }

    /** Synchronous review used as a mandatory pre-submit "quick check" the requestor must pass. */
    public Map<String, Object> quickCheck(UUID contractId, UUID userId, List<String> ruleCodes) {
        Contract c = contracts.findById(contractId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Contract not found"));
        drafting.syncFromEditor(contractId, userId);
        ContractVersion latest = versions.findByContractIdOrderByVersionNoDesc(c.id).stream().findFirst().orElse(null);
        String body = latest == null ? null : latest.bodyText;
        if (body == null || body.isBlank())
            throw new ApiExceptions.BadRequestException("No assembled document text to review yet — assemble the document first.");

        AiReviewRun run = new AiReviewRun();
        run.contractId = contractId;
        run.requestedBy = userId;
        run.status = "RUNNING";
        runs.save(run);

        try {
            runCore(run, c, List.of(), ruleCodes, body);
        } catch (Exception e) {
            run.status = "FAILED";
            run.error = e.getMessage() == null ? e.toString() : e.getMessage();
            run.completedAt = Instant.now();
            runs.save(run);
        }
        Map<String, Object> out = new LinkedHashMap<>(view(run));
        long critical = ((List<?>) out.getOrDefault("findings", List.of())).stream()
                .filter(f -> "CRITICAL".equalsIgnoreCase(String.valueOf(((Map<?, ?>) f).get("severity"))))
                .count();
        out.put("criticalCount", critical);
        return out;
    }

    private void execute(UUID runId, UUID userId, List<UUID> playbookIds, List<String> ruleCodes, String body) {
        AiReviewRun run = runs.findById(runId).orElse(null);
        if (run == null) return;
        Contract c = contracts.findById(run.contractId).orElse(null);
        if (c == null) {
            run.status = "FAILED";
            run.error = "Contract no longer exists";
            run.completedAt = Instant.now();
            runs.save(run);
            return;
        }
        try {
            runCore(run, c, playbookIds, ruleCodes, body);
        } catch (Exception e) {
            log.warn("Background review {} failed: {}", runId, e.toString());
        }
    }

    /** Shared review execution over one selected-rule scope; persists results and risks. */
    private void runCore(AiReviewRun run, Contract c, List<UUID> playbookIds, List<String> ruleCodes, String body) {
        String playbookText = playbookText(playbookIds == null ? List.<UUID>of() : playbookIds);
        try {
            StringBuilder rules = new StringBuilder();
            for (var r : reviewRules.findByIsActiveTrueOrderBySortAsc()) {
                if (ruleCodes != null && !ruleCodes.isEmpty() && !ruleCodes.contains(r.code)) continue;
                rules.append("- [").append(r.code).append(" | ").append(r.severity).append("] ")
                        .append(r.label).append(": ").append(r.instruction).append("\n");
            }
            if (rules.length() == 0) rules.append("(no review rules configured)");

            AiService.ReviewResult r = ai.reviewDocument(body, rules.toString(), playbookText, run.requestedBy, run.contractId);
            run.status = "DONE";
            run.overall = r.overall();
            run.findings = Json.write(r.findings());
            run.modelId = ai.modelId();
            run.completedAt = Instant.now();
            runs.save(run);
            recordRisks(contracts.findById(run.contractId).orElse(null), run.requestedBy, r.findings());
        } catch (Exception e) {
            run.status = "FAILED";
            run.error = e.getMessage() == null ? e.toString() : e.getMessage();
            run.completedAt = Instant.now();
            runs.save(run);
        }
    }

    private String playbookText(List<UUID> playbookIds) {
        StringBuilder docText = new StringBuilder();
        for (UUID pid : playbookIds == null ? List.<UUID>of() : playbookIds) {
            Playbook p = playbooks.findById(pid).orElse(null);
            if (p == null || !p.isActive) continue;
            String stripped = p.bodyHtml == null ? "" : p.bodyHtml.replaceAll("<[^>]*>", " ")
                    .replaceAll("\\s+", " ").trim();
            if (stripped.isBlank()) continue;
            if (stripped.length() > 6000) stripped = stripped.substring(0, 6000) + "…";
            docText.append("## Playbook: ").append(p.name).append("\n").append(stripped).append("\n\n");
        }
        return docText.toString();
    }

    /** Persist AI findings as AI_REVIEW risks; skip ones already open under the same title. */
    private void recordRisks(Contract c, UUID userId, List<Map<String, Object>> findings) {
        for (Map<String, Object> f : findings) {
            String title = String.valueOf(f.getOrDefault("title", "")).trim();
            if (title.isBlank()) continue;
            String dedupe = title.toLowerCase().replaceAll("\\s+", " ");
            boolean exists = risks.findByContractIdAndStatus(c.id, "OPEN").stream()
                    .anyMatch(r -> dedupe.equals(r.dedupeKey));
            if (exists) continue;
            ContractRisk risk = new ContractRisk();
            risk.contractId = c.id;
            risk.title = title;
            risk.severity = String.valueOf(f.getOrDefault("severity", "MEDIUM"));
            risk.detail = String.valueOf(f.getOrDefault("detail", ""));
            risk.location = String.valueOf(f.getOrDefault("location", ""));
            risk.source = "AI_REVIEW";
            risk.dedupeKey = dedupe;
            risk.createdBy = userId;
            risks.save(risk);
        }
    }

    /** Latest run for a contract (any status), or null. */
    public Map<String, Object> latest(UUID contractId) {
        return runs.findFirstByContractIdOrderByCreatedAtDesc(contractId).map(this::view).orElse(null);
    }

    private Map<String, Object> view(AiReviewRun r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.id);
        m.put("contractId", r.contractId);
        m.put("status", r.status);
        m.put("overall", r.overall);
        m.put("findings", r.findings == null ? List.of() : Json.readListOfMaps(r.findings));
        m.put("error", r.error);
        m.put("createdAt", r.createdAt.toString());
        m.put("completedAt", r.completedAt == null ? null : r.completedAt.toString());
        return m;
    }
}