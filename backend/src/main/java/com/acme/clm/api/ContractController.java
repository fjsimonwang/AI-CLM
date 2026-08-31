package com.acme.clm.api;

import com.acme.clm.config.CurrentUser;
import com.acme.clm.domain.Contract;
import com.acme.clm.domain.ContractAttachment;
import com.acme.clm.repo.Repos;
import com.acme.clm.service.AccessService;
import com.acme.clm.service.ContractService;
import com.acme.clm.service.EffectiveTermsResolver;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    private final ContractService service;
    private final EffectiveTermsResolver effectiveTerms;
    private final com.acme.clm.service.RiskService risks;
    private final AccessService access;
    private final Repos.Contracts contracts;
    private final Repos.ContractAttachments contractAttachments;
    private final CurrentUser current;

    public ContractController(ContractService service, EffectiveTermsResolver effectiveTerms,
                              com.acme.clm.service.RiskService risks, AccessService access,
                              Repos.Contracts contracts, Repos.ContractAttachments contractAttachments,
                              CurrentUser current) {
        this.service = service;
        this.effectiveTerms = effectiveTerms;
        this.risks = risks;
        this.access = access;
        this.contracts = contracts;
        this.contractAttachments = contractAttachments;
        this.current = current;
    }

    @GetMapping
    public List<Map<String, Object>> list(
            @RequestParam(required = false) String entityRegion,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String counterparty,
            @RequestParam(required = false) Integer expiringWithinDays,
            @RequestParam(required = false) BigDecimal minValue,
            @RequestParam(required = false) BigDecimal maxValue) {
        return service.list(current.id(), entityRegion, type, status, counterparty, expiringWithinDays, minValue, maxValue);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable UUID id) { return service.get(id, current.id()); }

    @GetMapping("/{id}/effective-terms")
    public Map<String, EffectiveTermsResolver.ResolvedTerm> effectiveTerms(
            @PathVariable UUID id,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return effectiveTerms.resolve(id, asOf);
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody ContractService.CreateRequest req) {
        return service.create(req, current.id());
    }

    @PatchMapping("/{id}/status")
    public Map<String, Object> updateStatus(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return service.updateStatus(id, body.get("status"), current.id());
    }

    public record AgentCollabRequest(boolean enabled) {}

    @PostMapping("/{id}/agent-collab")
    public Map<String, Object> setAgentCollab(@PathVariable UUID id, @RequestBody AgentCollabRequest req) {
        return service.setAgentCollab(id, req.enabled(), current.id());
    }

    // ---- risk register ----

    @org.springframework.web.bind.annotation.GetMapping("/{id}/risks")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('PERM_VIEW_CONTRACTS')")
    public List<Map<String, Object>> risks(@PathVariable UUID id) {
        return risks.list(id);
    }

    @org.springframework.web.bind.annotation.PostMapping("/{id}/risks")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('PERM_EDIT_CONTRACT')")
    public Map<String, Object> addRisk(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return risks.add(id, current.id(), body);
    }

    @org.springframework.web.bind.annotation.PostMapping("/{id}/risks/{riskId}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('PERM_EDIT_CONTRACT')")
    public Map<String, Object> updateRisk(@PathVariable UUID id,
                                          @org.springframework.web.bind.annotation.PathVariable UUID riskId,
                                          @RequestBody Map<String, Object> body) {
        return risks.update(id, riskId, current.id(), body);
    }

    public record ParticipantRequest(UUID userId, String role) {}

    @PostMapping("/{id}/participants")
    public Map<String, Object> addParticipant(@PathVariable UUID id, @RequestBody ParticipantRequest req) {
        return service.addParticipant(id, req.userId(), req.role(), current.id());
    }

    @DeleteMapping("/{id}/participants/{userId}")
    public Map<String, Object> removeParticipant(@PathVariable UUID id, @PathVariable UUID userId) {
        return service.removeParticipant(id, userId, current.id());
    }

    @GetMapping("/{id}/relations")
    public Map<String, Object> relations(@PathVariable UUID id) {
        return service.relations(id, current.id());
    }

    @PostMapping("/{id}/relations/detect")
    public Map<String, Object> detectRelations(@PathVariable UUID id) {
        return service.detectRelations(id, current.id());
    }

    @PostMapping("/{id}/relations/{relId}/confirm")
    public Map<String, Object> confirmRelation(@PathVariable UUID id, @PathVariable UUID relId) {
        return service.decideRelation(id, relId, true, current.id());
    }

    @PostMapping("/{id}/relations/{relId}/reject")
    public Map<String, Object> rejectRelation(@PathVariable UUID id, @PathVariable UUID relId) {
        return service.decideRelation(id, relId, false, current.id());
    }

    // ---- supporting attachments ----

    @GetMapping("/{id}/attachments")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('PERM_VIEW_CONTRACTS')")
    public List<Map<String, Object>> attachments(@PathVariable UUID id) {
        requireCanView(id);
        return contractAttachments.findByContractIdOrderByCreatedAtAsc(id).stream()
                .<Map<String, Object>>map(a -> Map.of(
                        "id", a.id, "filename", a.filename,
                        "contentType", a.contentType == null ? "" : a.contentType,
                        "size", a.sizeBytes, "createdAt", a.createdAt))
                .toList();
    }

    @GetMapping("/{id}/attachments/{attachmentId}/file")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('PERM_VIEW_CONTRACTS')")
    public org.springframework.http.ResponseEntity<byte[]> attachmentFile(@PathVariable UUID id,
                                                                          @PathVariable UUID attachmentId) {
        requireCanView(id);
        ContractAttachment a = contractAttachments.findById(attachmentId)
                .filter(x -> id.equals(x.contractId))
                .orElseThrow(() -> new com.acme.clm.common.ApiExceptions.NotFoundException("Attachment not found"));
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename*=UTF-8''" + java.net.URLEncoder
                        .encode(a.filename == null ? "attachment.bin" : a.filename, java.nio.charset.StandardCharsets.UTF_8)
                        .replace("+", "%20"))
                .header("Content-Type", a.contentType == null ? "application/octet-stream" : a.contentType)
                .body(a.content);
    }

    private void requireCanView(UUID contractId) {
        Contract c = contracts.findById(contractId)
                .orElseThrow(() -> new com.acme.clm.common.ApiExceptions.NotFoundException("Contract not found"));
        if (!access.canView(current.id(), c))
            throw new com.acme.clm.common.ApiExceptions.NotFoundException("Contract not found");
    }
}
