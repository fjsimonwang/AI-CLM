package com.acme.clm.api;

import com.acme.clm.common.ApiExceptions;
import com.acme.clm.config.CurrentUser;
import com.acme.clm.domain.Contract;
import com.acme.clm.repo.Repos;
import com.acme.clm.service.DraftingService;
import com.acme.clm.service.WordEditorClient;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contracts/{contractId}/document")
@PreAuthorize("hasAuthority('PERM_VIEW_CONTRACTS')")
public class DocumentController {

    private final Repos.Contracts contracts;
    private final Repos.ContractVersions versions;
    private final WordEditorClient wordEditor;
    private final DraftingService drafting;
    private final CurrentUser current;

    public DocumentController(Repos.Contracts contracts, Repos.ContractVersions versions,
                              WordEditorClient wordEditor, DraftingService drafting, CurrentUser current) {
        this.contracts = contracts;
        this.versions = versions;
        this.wordEditor = wordEditor;
        this.drafting = drafting;
        this.current = current;
    }

    @GetMapping
    public Map<String, Object> get(@PathVariable UUID contractId) {
        Contract c = contracts.findById(contractId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Contract not found"));

        // lazily assemble a document the first time it's opened (never for third-party paper —
        // the uploaded paper must not be replaced by a template)
        boolean paper = "MIGRATED".equals(c.source);
        if (!paper && (c.editorDocumentId == null || c.editorDocumentId.isBlank()) && wordEditor.enabled()) {
            try {
                drafting.assemble(contractId, current.id());
                c = contracts.findById(contractId).orElseThrow();
            } catch (Exception ignored) { }
        }

        // the drafter can keep editing their own document through the review phase (never on
        // third-party paper); everyone else needs the explicit document-edit permission
        boolean canEdit = canEditContract(c);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", wordEditor.enabled());
        m.put("editorDocId", c.editorDocumentId);
        m.put("editorBaseUrl", wordEditor.publicBaseUrl());
        m.put("canEdit", canEdit);
        m.put("source", c.source);
        m.put("readOnlyReason", paper ? "This document is the uploaded third-party paper and cannot be edited." : null);
        m.put("token", c.editorDocumentId == null ? "" :
                wordEditor.mintScopedToken(c.editorDocumentId, contractId.toString()));
        var latest = versions.findByContractIdOrderByVersionNoDesc(contractId).stream().findFirst().orElse(null);
        m.put("bodyText", latest == null ? null : latest.bodyText);
        m.put("changeSummary", latest == null ? null : latest.changeSummary);
        return m;
    }

    /** Pull the current editor content into the latest contract version (so review sees edits). */
    @PostMapping("/sync")
    public Map<String, Object> sync(@PathVariable UUID contractId) {
        Contract c = contracts.findById(contractId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Contract not found"));
        if (!canEditContract(c)) return Map.of("changed", false);
        boolean changed = drafting.syncFromEditor(contractId, current.id());
        return Map.of("changed", changed);
    }

    private boolean canEditContract(Contract c) {
        boolean paper = "MIGRATED".equals(c.source);
        boolean ownerEditing = c.ownerUserId != null && c.ownerUserId.equals(current.id())
                && ("DRAFT".equals(c.status) || "IN_REVIEW".equals(c.status));
        return !paper && (hasAuthority("PERM_EDIT_DOCUMENT") || ownerEditing)
                && !"EXECUTED".equals(c.status) && !"CLOSED_REJECTED".equals(c.status);
    }

    /** Draft owners may assemble their own draft before submitting; others need PERM_EDIT_DOCUMENT. */
    @PostMapping("/assemble")
    public DraftingService.DraftResult assemble(@PathVariable UUID contractId) {
        Contract c = contracts.findById(contractId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Contract not found"));
        boolean ownerDraft = c.ownerUserId != null && c.ownerUserId.equals(current.id()) && "DRAFT".equals(c.status);
        boolean paper = "MIGRATED".equals(c.source);
        if (!hasAuthority("PERM_EDIT_DOCUMENT") && !ownerDraft) {
            throw new ApiExceptions.ForbiddenException("You don't have permission to assemble this document.");
        }
        if (paper) {
            throw new ApiExceptions.BadRequestException(
                    "This document is the uploaded third-party paper and cannot be re-assembled.");
        }
        return drafting.assemble(contractId, current.id());
    }

    private boolean hasAuthority(String a) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(x -> x.getAuthority().equals(a));
    }
}
