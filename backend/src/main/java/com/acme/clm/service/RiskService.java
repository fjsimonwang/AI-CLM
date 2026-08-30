package com.acme.clm.service;

import com.acme.clm.common.ApiExceptions;
import com.acme.clm.domain.ContractRisk;
import com.acme.clm.repo.Repos;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** The per-contract risk register. */
@Service
public class RiskService {

    private final Repos.ContractRisks risks;
    private final AuditService audit;

    public RiskService(Repos.ContractRisks risks, AuditService audit) {
        this.risks = risks;
        this.audit = audit;
    }

    public List<Map<String, Object>> list(UUID contractId) {
        return risks.findByContractIdOrderByCreatedAtDesc(contractId).stream().map(this::view).toList();
    }

    public Map<String, Object> add(UUID contractId, UUID userId, Map<String, Object> body) {
        String title = str(body, "title");
        if (title == null || title.isBlank())
            throw new ApiExceptions.BadRequestException("A risk title is required");
        ContractRisk r = new ContractRisk();
        r.contractId = contractId;
        r.title = title.trim();
        r.category = str(body, "category");
        String sev = str(body, "severity");
        r.severity = sev == null || sev.isBlank() ? "MEDIUM" : sev;
        r.detail = str(body, "detail");
        r.source = "MANUAL";
        r.createdBy = userId;
        save(r, "RISK_LOGGED", userId);
        return view(r);
    }

    public Map<String, Object> update(UUID contractId, UUID riskId, UUID userId, Map<String, Object> body) {
        ContractRisk r = risks.findById(riskId).orElseThrow(() -> new ApiExceptions.NotFoundException("Risk not found"));
        if (!r.contractId.equals(contractId))
            throw new ApiExceptions.NotFoundException("Risk not found");
        if (body.containsKey("title")) r.title = str(body, "title");
        if (body.containsKey("category")) r.category = str(body, "category");
        if (body.containsKey("severity")) r.severity = str(body, "severity");
        if (body.containsKey("detail")) r.detail = str(body, "detail");
        if (body.containsKey("status")) {
            String st = str(body, "status");
            if ("CLOSED".equals(st) && !"CLOSED".equals(r.status)) {
                r.status = "CLOSED";
                r.resolution = str(body, "resolution");
                r.closedBy = userId;
                r.closedAt = Instant.now();
            } else if ("OPEN".equals(st) && !"OPEN".equals(r.status)) {
                r.status = "OPEN";
                r.resolution = null;
                r.closedBy = null;
                r.closedAt = null;
            }
        }
        save(r, "RISK_UPDATED", userId);
        return view(r);
    }

    private void save(ContractRisk r, String action, UUID userId) {
        Map<String, Object> snap = Map.of("title", r.title, "severity", r.severity,
                "category", String.valueOf(r.category), "status", r.status, "source", r.source);
        risks.save(r);
        audit.record("CONTRACT_RISK", r.id.toString(), action, userId, null, snap);
    }

    private Map<String, Object> view(ContractRisk r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.id);
        m.put("contractId", r.contractId);
        m.put("title", r.title);
        m.put("category", r.category);
        m.put("severity", r.severity);
        m.put("detail", r.detail);
        m.put("location", r.location);
        m.put("source", r.source);
        m.put("status", r.status);
        m.put("resolution", r.resolution);
        m.put("dedupeKey", r.dedupeKey);
        m.put("createdAt", r.createdAt.toString());
        m.put("closedAt", r.closedAt == null ? null : r.closedAt.toString());
        return m;
    }

    private static String str(Map<String, Object> b, String k) {
        Object v = b.get(k);
        return v == null ? null : String.valueOf(v);
    }
}