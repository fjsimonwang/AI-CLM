package com.acme.clm.service;

import com.acme.clm.ai.AiService;
import com.acme.clm.common.ApiExceptions;
import com.acme.clm.common.Json;
import com.acme.clm.domain.*;
import com.acme.clm.repo.Repos;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IntakeService {

    private static final Logger log = LoggerFactory.getLogger(IntakeService.class);

    private final Repos.IntakeSessions sessions;
    private final Repos.IntakeAttachments intakeAttachments;
    private final Repos.ContractAttachments contractAttachments;
    private final Repos.ContractTypes types;
    private final Repos.Users users;
    private final Repos.LegalEntities entities;
    private final Repos.Parties parties;
    private final Repos.Contracts contracts;
    private final AiService ai;
    private final TriageService triage;
    private final PrecedentService precedents;
    private final ContractService contractService;
    private final DraftingService drafting;
    private final WorkflowService workflow;
    private final FieldCatalog catalog;
    private final AuditService audit;
    private final DocToHtmlService docToHtml;
    private final IntakeService self;

    public IntakeService(Repos.IntakeSessions sessions, Repos.IntakeAttachments intakeAttachments,
                         Repos.ContractAttachments contractAttachments, Repos.ContractTypes types, Repos.Users users,
                         Repos.LegalEntities entities, Repos.Parties parties, Repos.Contracts contracts,
                         AiService ai, TriageService triage, PrecedentService precedents,
                         ContractService contractService, DraftingService drafting, WorkflowService workflow,
                         FieldCatalog catalog, AuditService audit, DocToHtmlService docToHtml,
                         @Lazy IntakeService self) {
        this.sessions = sessions;
        this.intakeAttachments = intakeAttachments;
        this.contractAttachments = contractAttachments;
        this.types = types;
        this.users = users;
        this.entities = entities;
        this.parties = parties;
        this.contracts = contracts;
        this.ai = ai;
        this.triage = triage;
        this.precedents = precedents;
        this.contractService = contractService;
        this.drafting = drafting;
        this.workflow = workflow;
        this.catalog = catalog;
        this.audit = audit;
        this.docToHtml = docToHtml;
        this.self = self;
    }

    // ------------------------------------------------------------------ create

    @Transactional
    public Map<String, Object> create(UUID userId) {
        AppUser u = users.findById(userId).orElseThrow();
        IntakeSession s = new IntakeSession();
        s.requesterUserId = userId;
        s.channel = "CHAT";
        s.status = "OPEN";

        Map<String, Object> captured = new LinkedHashMap<>();
        Map<String, Object> provenance = new LinkedHashMap<>();
        if (u.defaultEntityId != null) {
            entities.findById(u.defaultEntityId).ifPresent(e -> {
                captured.put("contracting_entity_id", e.id.toString());
                captured.put("contracting_entity", e.shortName);
                captured.put("governing_law", e.defaultGoverningLaw);
                provenance.put("contracting_entity_id", "PREFILLED_FROM_HR");
                provenance.put("governing_law", "INFERRED_FROM_CONTEXT");
            });
        }
        captured.put("requested_by", u.displayName);
        captured.put("department", u.department);
        provenance.put("requested_by", "PREFILLED_FROM_HR");
        provenance.put("department", "PREFILLED_FROM_HR");

        s.capturedFields = Json.write(captured);
        s.fieldProvenance = Json.write(provenance);
        s.confidenceScores = "{}";
        s.conversationHistory = Json.write(List.of(Map.of(
                "role", "assistant",
                "content", "Hi " + firstName(u.displayName) + " — tell me what you need in plain language. "
                        + "For example: \"I need a mutual NDA with a logistics vendor in Germany for 2 years\".")));
        sessions.save(s);
        audit.record("INTAKE_SESSION", s.id.toString(), "CREATED", userId, null, null);
        return view(s);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID id) {
        return view(sessions.findById(id).orElseThrow(() -> new ApiExceptions.NotFoundException("Intake session not found")));
    }

    /**
     * Seed the session from an existing contract the user picked on the start screen.
     * mode: SIMILAR (new contract on the same terms), SAME_PARTY (new contract, same counterparty,
     * choose a fresh type), or AMEND (an amendment to the source).
     */
    @Transactional
    public Map<String, Object> startFrom(UUID sessionId, UUID userId, UUID sourceContractId, String mode) {
        IntakeSession s = requireOpen(sessionId);
        Contract src = contracts.findById(sourceContractId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Contract not found"));

        Map<String, Object> captured = Json.readMap(s.capturedFields);
        Map<String, String> provenance = strMap(Json.readMap(s.fieldProvenance));
        Map<String, Object> attrs = Json.readMap(src.typeAttributes);
        String cpName = contractService.counterpartyName(sourceContractId);
        String m = mode == null ? "SIMILAR" : mode.toUpperCase();

        captured.put("contracting_entity_id", src.contractingEntityId.toString());
        entities.findById(src.contractingEntityId).ifPresent(e -> captured.put("contracting_entity", e.shortName));
        if (src.governingLawCode != null) captured.put("governing_law", src.governingLawCode);
        provenance.put("contracting_entity_id", "PREFILLED_FROM_PRECEDENT");
        provenance.put("governing_law", "PREFILLED_FROM_PRECEDENT");

        String assistant;
        if ("SAME_PARTY".equals(m)) {
            if (cpName != null) { captured.put("counterparty_name", cpName); provenance.put("counterparty_name", "PREFILLED_FROM_PRECEDENT"); }
            assistant = "Starting a new contract with **" + (cpName == null ? "the same counterparty" : cpName)
                    + "**. What type of contract do you need this time?";
        } else if ("AMEND".equals(m)) {
            captured.put("contract_type_code", src.contractTypeCode);
            if (cpName != null) captured.put("counterparty_name", cpName);
            captured.put("_start_mode", "AMEND");
            captured.put("_source_contract_id", sourceContractId.toString());
            provenance.put("contract_type_code", "PREFILLED_FROM_PRECEDENT");
            assistant = "Amending **" + src.contractNumber + "** (" + src.title + "). Tell me what's changing — "
                    + "e.g. a new value, extended term, or updated payment terms — and I'll draft the amendment.";
        } else { // SIMILAR
            captured.put("contract_type_code", src.contractTypeCode);
            if (cpName != null) captured.put("counterparty_name", cpName);
            attrs.forEach((k, v) -> { if (v != null && !String.valueOf(v).isBlank()) captured.put(k, v); });
            provenance.put("contract_type_code", "PREFILLED_FROM_PRECEDENT");
            if (cpName != null) provenance.put("counterparty_name", "PREFILLED_FROM_PRECEDENT");
            attrs.keySet().forEach(k -> provenance.put(k, "PREFILLED_FROM_PRECEDENT"));
            assistant = "I've carried over everything from **" + src.contractNumber + "** as the starting point — "
                    + "same " + src.contractTypeCode + (cpName == null ? "" : " with " + cpName) + " and the same terms. "
                    + "What should be different for this one?";
        }
        s.contractTypeCode = currentType(s, captured);

        // link the source as a precedent for drafting
        captured.put("_precedent_contract_id", sourceContractId.toString());

        List<Map<String, Object>> history = Json.readListOfMaps(s.conversationHistory);
        history.add(Map.of("role", "assistant", "content", assistant));
        persist(s, captured, provenance, Json.readMap(s.confidenceScores), history);
        audit.record("INTAKE_SESSION", sessionId.toString(), "STARTED_FROM_CONTRACT", userId, null,
                Map.of("source", src.contractNumber, "mode", m));

        Map<String, Object> out = view(s);
        out.put("assistantReply", assistant);
        out.put("precedents", suggestPrecedents(currentType(s, captured), captured));
        return out;
    }

    // ------------------------------------------------------------------ 3rd-party paper upload

    /**
     * The user uploaded the counterparty's paper contract. Convert it, let AI extract the
     * intake fields (provenance keeps the confirm-on-review flow), and keep the paper's HTML
     * so submit can use it verbatim as the contract document.
     */
    @Transactional
    public Map<String, Object> uploadPaper(UUID sessionId, UUID userId, org.springframework.web.multipart.MultipartFile file) {
        IntakeSession s = requireOpen(sessionId);
        String html = docToHtml.convert(file);
        String text = docToHtml.toPlainText(html);
        if (text.length() > 120_000) text = text.substring(0, 120_000);

        Map<String, Object> captured = Json.readMap(s.capturedFields);
        Map<String, String> provenance = strMap(Json.readMap(s.fieldProvenance));
        Map<String, Object> confidence = Json.readMap(s.confidenceScores);

        AiService.PaperExtraction ex = ai.extractPaperContract(text, fieldGuide(null), Json.write(captured),
                userId, sessionId);

        FieldCatalog.Normalized n = catalog.normalize(currentType(s, captured), captured, provenance,
                ex.capturedFields(), false);
        captured = n.values();
        provenance = n.provenance();
        ex.fieldConfidence().forEach((k, v) -> confidence.put(k, v));

        s.paperFilename = file.getOriginalFilename();
        s.paperBodyHtml = html;
        s.contractTypeCode = currentType(s, captured);

        if (blankToNull(captured.get("title")) == null && s.paperFilename != null) {
            String fn = s.paperFilename.replaceAll("\\.[^.]*$", "").replaceAll("[_-]+", " ").strip();
            if (!fn.isBlank()) captured.put("title", fn);
        }

        List<Map<String, Object>> history = Json.readListOfMaps(s.conversationHistory);
        history.add(Map.of("role", "assistant", "content", ex.assistantReply()));
        persist(s, captured, provenance, confidence, history);
        audit.record("INTAKE_SESSION", sessionId.toString(), "PAPER_UPLOADED", userId, null,
                Map.of("file", s.paperFilename == null ? "" : s.paperFilename));

        Map<String, Object> out = view(s);
        out.put("assistantReply", ex.assistantReply());
        out.put("aiInteractionId", ex.interactionId());
        out.put("precedents", suggestPrecedents(currentType(s, captured), captured));
        return out;
    }

    // ------------------------------------------------------------------ supporting attachments

    private static final long MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024;

    public Map<String, Object> addAttachment(UUID sessionId, UUID userId,
                                             org.springframework.web.multipart.MultipartFile file) {
        IntakeSession s = requireOpen(sessionId);
        if (file == null || file.isEmpty()) throw new ApiExceptions.BadRequestException("Choose a file to attach.");
        if (file.getSize() > MAX_ATTACHMENT_BYTES)
            throw new ApiExceptions.BadRequestException("Attachment too large (max 10 MB).");
        IntakeAttachment a = new IntakeAttachment();
        a.intakeSessionId = sessionId;
        a.filename = String.valueOf(file.getOriginalFilename());
        a.contentType = file.getContentType();
        a.sizeBytes = file.getSize();
        try { a.content = file.getBytes(); }
        catch (java.io.IOException e) { throw new ApiExceptions.BadRequestException("Could not read the file: " + e.getMessage()); }
        intakeAttachments.save(a);
        audit.record("INTAKE_SESSION", sessionId.toString(), "ATTACHMENT_ADDED", userId, null,
                Map.of("file", a.filename));
        return view(s);
    }

    public List<Map<String, Object>> listAttachments(UUID sessionId) {
        return intakeAttachments.findByIntakeSessionIdOrderByCreatedAtAsc(sessionId)
                .stream().map(this::attachmentView).toList();
    }

    public Map<String, Object> removeAttachment(UUID sessionId, UUID attachmentId, UUID userId) {
        requireOpen(sessionId);
        IntakeAttachment a = intakeAttachments.findById(attachmentId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Attachment not found"));
        if (!sessionId.equals(a.intakeSessionId))
            throw new ApiExceptions.NotFoundException("Attachment not found");
        intakeAttachments.delete(a);
        audit.record("INTAKE_SESSION", sessionId.toString(), "ATTACHMENT_REMOVED", userId, null,
                Map.of("file", a.filename));
        return view(sessions.findById(sessionId).orElseThrow());
    }

    public IntakeAttachment attachmentFile(UUID sessionId, UUID attachmentId) {
        IntakeAttachment a = intakeAttachments.findById(attachmentId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Attachment not found"));
        if (!sessionId.equals(a.intakeSessionId))
            throw new ApiExceptions.NotFoundException("Attachment not found");
        return a;
    }

    /** HTML of the uploaded third-party paper, for preview before submit. */
    public String paperHtml(UUID sessionId) {
        IntakeSession s = sessions.findById(sessionId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Intake session not found"));
        if (s.paperBodyHtml == null || s.paperBodyHtml.isBlank())
            throw new ApiExceptions.NotFoundException("No paper uploaded in this session");
        return s.paperBodyHtml;
    }

    private Map<String, Object> attachmentView(IntakeAttachment a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.id);
        m.put("filename", a.filename);
        m.put("contentType", a.contentType);
        m.put("size", a.sizeBytes);
        m.put("createdAt", a.createdAt);
        return m;
    }

    /**
     * Mirror the intake session's supporting attachments onto the contract. Idempotent and called
     * on every submit (first submit AND every revise-and-resubmit) so files the requester adds
     * while a returned request is reopened for revision actually reach the contract — the
     * auto-rejection rules and the contract's "Supporting documents" list read the CONTRACT's
     * attachments, not the session's.
     */
    private void carryAttachmentsToContract(IntakeSession s, UUID contractId, UUID userId) {
        List<IntakeAttachment> sessionFiles = intakeAttachments.findByIntakeSessionIdOrderByCreatedAtAsc(s.id);
        List<ContractAttachment> existing = contractAttachments.findByContractIdOrderByCreatedAtAsc(contractId);
        java.util.function.BiFunction<String, Long, String> key = (name, size) -> (name == null ? "" : name) + " " + size;
        Set<String> want = sessionFiles.stream().map(a -> key.apply(a.filename, a.sizeBytes)).collect(java.util.stream.Collectors.toSet());
        Set<String> have = new java.util.HashSet<>();

        // remove contract attachments the session no longer has (all contract attachments originate here)
        for (ContractAttachment ca : existing) {
            String k = key.apply(ca.filename, ca.sizeBytes);
            if (want.contains(k) && have.add(k)) continue; // keep the first of any duplicate
            contractAttachments.delete(ca);
        }
        // add session attachments the contract is missing
        for (IntakeAttachment a : sessionFiles) {
            String k = key.apply(a.filename, a.sizeBytes);
            if (!have.add(k)) continue; // already present (or a duplicate name+size)
            ContractAttachment ca = new ContractAttachment();
            ca.contractId = contractId;
            ca.filename = a.filename;
            ca.contentType = a.contentType;
            ca.sizeBytes = a.sizeBytes;
            ca.content = a.content;
            contractAttachments.save(ca);
        }
    }

    /** Saved drafts stay listed until they are submitted for approval (contract leaves DRAFT). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listMine(UUID userId) {
        return sessions.findByRequesterUserIdOrderByUpdatedAtDesc(userId).stream()
                .filter(s -> s.saved && (s.resultingContractId == null || isDraftStatus(s.resultingContractId)))
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", s.id);
                    m.put("status", s.status);
                    m.put("requestNumber", s.requestNumber == null ? "" : s.requestNumber);
                    m.put("contractType", s.contractTypeCode == null ? "" : s.contractTypeCode);
                    m.put("updatedAt", s.updatedAt);
                    m.put("resultingContractId", s.resultingContractId == null ? "" : s.resultingContractId);
                    if (s.resultingContractId != null) {
                        contracts.findById(s.resultingContractId)
                                .ifPresent(c -> m.put("contractNumber", c.contractNumber));
                    }
                    return m;
                }).toList();
    }

    private boolean isDraftStatus(UUID contractId) {
        return contracts.findById(contractId).map(c -> "DRAFT".equals(c.status)).orElse(false);
    }

    private String nextRequestNumber() {
        String prefix = "REQ-" + java.time.Year.now().getValue() + "-";
        int seq = sessions.findTopByRequestNumberStartingWithOrderByRequestNumberDesc(prefix)
                .map(s -> {
                    try { return Integer.parseInt(s.requestNumber.substring(prefix.length())); }
                    catch (Exception e) { return 0; }
                })
                .orElse(0) + 1;
        return prefix + String.format("%04d", seq);
    }

    @Transactional
    public Map<String, Object> saveDraft(UUID id, UUID userId) {
        IntakeSession s = sessions.findById(id)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Intake session not found"));
        if (!s.requesterUserId.equals(userId))
            throw new ApiExceptions.NotFoundException("Intake session not found");
        s.saved = true;
        if (s.requestNumber == null) s.requestNumber = nextRequestNumber();
        s.updatedAt = Instant.now();
        sessions.save(s);
        audit.record("INTAKE_SESSION", id.toString(), "DRAFT_SAVED", userId, null, null);
        return view(s);
    }

    /**
     * Reopen the intake session behind a rejected contract so the requester can revise the request
     * in the intake workspace and resubmit. The session keeps its {@code resultingContractId}, so
     * the eventual resubmit patches that same contract (see {@link #createContract}).
     */
    @Transactional
    public Map<String, Object> reopenForRevision(UUID contractId, UUID userId) {
        Contract c = contracts.findById(contractId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Contract not found"));
        if (!userId.equals(c.ownerUserId) && !userId.equals(c.createdBy)) {
            throw new ApiExceptions.ForbiddenException("Only the requester can revise this request.");
        }
        if (!"DRAFT".equals(c.status)) {
            throw new ApiExceptions.BadRequestException("Only a draft request can be edited — recall it from review first.");
        }
        if (c.intakeSessionId == null) {
            throw new ApiExceptions.BadRequestException("This contract was not created through intake.");
        }
        IntakeSession s = sessions.findById(c.intakeSessionId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Intake session not found"));
        s.status = "OPEN";
        s.saved = true;
        if (s.requestNumber == null) s.requestNumber = nextRequestNumber();
        s.updatedAt = Instant.now();
        sessions.save(s);
        audit.record("INTAKE_SESSION", s.id.toString(), "REOPENED_FOR_REVISION", userId, null,
                Map.of("contractId", contractId.toString()));
        return view(s);
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        IntakeSession s = sessions.findById(id)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Intake session not found"));
        if (!s.requesterUserId.equals(userId))
            throw new ApiExceptions.NotFoundException("Intake session not found");
        if (s.resultingContractId != null)
            throw new ApiExceptions.BadRequestException("This request already produced a contract and cannot be deleted.");
        intakeAttachments.deleteByIntakeSessionId(id);
        sessions.delete(s);
        audit.record("INTAKE_SESSION", id.toString(), "DELETED", userId, null, null);
    }

    // ------------------------------------------------------------------ conversational turn

    @Transactional
    public Map<String, Object> turn(UUID sessionId, UUID userId, String message) {
        IntakeSession s = requireOpen(sessionId);

        List<Map<String, Object>> history = Json.readListOfMaps(s.conversationHistory);
        history.add(Map.of("role", "user", "content", message));

        Map<String, Object> captured = Json.readMap(s.capturedFields);
        Map<String, String> provenance = strMap(Json.readMap(s.fieldProvenance));
        Map<String, Object> confidence = Json.readMap(s.confidenceScores);

        String typeBefore = currentType(s, captured);
        AiService.IntakeTurn t = ai.intakeTurn(sessionId, userId, renderHistory(history), message,
                fieldGuide(typeBefore), missingRequiredLabels(typeBefore, captured), captured);

        // deterministic normalization of everything the model returned (plan §6A.4)
        FieldCatalog.Normalized n = catalog.normalize(currentType(s, captured), captured, provenance,
                t.capturedFields(), false);
        captured = n.values();
        provenance = n.provenance();
        t.fieldConfidence().forEach((k, v) -> confidence.put(k, v));

        String type = currentType(s, captured);
        s.contractTypeCode = type;

        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", t.assistantReply());
        if (!t.questions().isEmpty()) assistantMsg.put("questions", t.questions());
        history.add(assistantMsg);
        persist(s, captured, provenance, confidence, history);

        Map<String, Object> out = view(s);
        out.put("assistantReply", t.assistantReply());
        out.put("clarifyingQuestion", t.clarifyingQuestion());
        out.put("aiInteractionId", t.interactionId());
        out.put("precedents", suggestPrecedents(type, captured));
        return out;
    }

    // ------------------------------------------------------------------ direct field edits

    @Transactional
    public Map<String, Object> updateFields(UUID sessionId, UUID userId, Map<String, Object> fields,
                                            List<String> confirmKeys) {
        IntakeSession s = requireOpen(sessionId);
        Map<String, Object> captured = Json.readMap(s.capturedFields);
        Map<String, String> provenance = strMap(Json.readMap(s.fieldProvenance));
        Map<String, Object> confidence = Json.readMap(s.confidenceScores);

        if (fields != null && !fields.isEmpty()) {
            FieldCatalog.Normalized n = catalog.normalize(currentType(s, captured), captured, provenance, fields, true);
            captured = n.values();
            provenance = n.provenance();
        }
        if (confirmKeys != null && !confirmKeys.isEmpty()) {
            provenance = catalog.confirm(provenance, confirmKeys);
        }
        s.contractTypeCode = currentType(s, captured);
        persist(s, captured, provenance, confidence, Json.readListOfMaps(s.conversationHistory));
        audit.record("INTAKE_SESSION", sessionId.toString(), "FIELDS_EDITED", userId, null,
                Map.of("keys", fields == null ? List.of() : new ArrayList<>(fields.keySet())));
        return view(s);
    }

    /** Quick-start from a type chip: pre-sets contract_type_code deterministically (no LLM round-trip). */
    @Transactional
    public Map<String, Object> startType(UUID sessionId, UUID userId, String type) {
        IntakeSession s = requireOpen(sessionId);
        if (type == null || type.isBlank()) {
            throw new com.acme.clm.common.ApiExceptions.BadRequestException("Missing contract type");
        }
        Map<String, Object> captured = Json.readMap(s.capturedFields);
        Map<String, String> provenance = strMap(Json.readMap(s.fieldProvenance));
        Map<String, Object> confidence = Json.readMap(s.confidenceScores);
        FieldCatalog.Normalized n = catalog.normalize(currentType(s, captured), captured, provenance,
                Map.of("contract_type_code", type), true);
        captured = n.values();
        provenance = n.provenance();
        s.contractTypeCode = currentType(s, captured);

        List<Map<String, Object>> conv = new ArrayList<>(Json.readListOfMaps(s.conversationHistory));
        conv.add(Map.of("role", "user", "content", "Start a new " + type));
        conv.add(Map.of("role", "assistant", "content",
                "Okay — new " + type + " it is. I've pre-set the contract type on the panel to the right; "
                        + "tell me the key details, starting with the counterparty."));
        persist(s, captured, provenance, confidence, conv);
        audit.record("INTAKE_SESSION", sessionId.toString(), "TYPE_QUICK_START", userId, null, Map.of("type", type));
        return view(s);
    }

    // ------------------------------------------------------------------ submit

    /**
     * Creates the contract as a DRAFT and assembles its document, but does NOT start the
     * approval workflow — the requester reviews the drafted document first, then submits for
     * approval from the contract screen.
     */
    public Map<String, Object> submit(UUID sessionId, UUID userId, UUID precedentContractId,
                                      Map<String, Object> fieldOverrides) {
        // A session that already produced a contract is a REVISION (the requester revising a
        // returned/rejected request). In that case the document must be left exactly as it was
        // last edited — we do NOT re-assemble it from the template here. The requester can still
        // trigger a fresh assembly explicitly with the "Re-assemble" button on the contract.
        boolean revising = sessions.findById(sessionId)
                .map(x -> x.resultingContractId != null).orElse(false);

        UUID contractId = self.createContract(sessionId, userId, precedentContractId, fieldOverrides);
        IntakeSession sess = sessions.findById(sessionId).orElseThrow();
        sess.saved = true; // moving to the draft preview counts as an explicit save
        if (sess.requestNumber == null) sess.requestNumber = nextRequestNumber();
        sessions.save(sess);
        boolean paper = sess.paperBodyHtml != null && !sess.paperBodyHtml.isBlank();

        if (revising) {
            Contract c = contracts.findById(contractId).orElseThrow();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("contractId", contractId);
            out.put("contractNumber", c.contractNumber);
            out.put("status", c.status);
            out.put("draftDeviations", List.of());
            out.put("clausesFromPrecedent", List.of());
            out.put("documentKept", true); // signals the UI that the last document version was retained
            if ("MIGRATED".equals(c.source)) {
                out.put("paperMode", true);
                out.put("paperFilename", sess.paperFilename);
            }
            return out;
        }

        List<Map<String, Object>> deviations = List.of();
        List<String> fromPrecedent = List.of();
        if (paper) {
            // the uploaded paper IS the document — skip template assembly entirely
            try {
                Contract c = contracts.findById(contractId).orElseThrow();
                drafting.assemblePaper(contractId, userId,
                        c.title == null ? "" : c.title, sess.paperBodyHtml, sess.paperFilename);
            } catch (Exception e) {
                log.warn("Paper assembly failed for {} (contract still created): {}", contractId, e.toString());
            }
        } else {
            try {
                DraftingService.DraftResult draft = drafting.assemble(contractId, userId);
                deviations = draft.deviationsToAcknowledge();
                fromPrecedent = draft.fromPrecedent();
            } catch (Exception e) {
                log.warn("Draft assembly failed for {} (contract still created): {}", contractId, e.toString());
            }
        }

        Contract c = contracts.findById(contractId).orElseThrow();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("contractId", contractId);
        out.put("contractNumber", c.contractNumber);
        out.put("status", c.status);
        out.put("draftDeviations", deviations);
        out.put("clausesFromPrecedent", fromPrecedent);
        if (paper) {
            out.put("paperMode", true);
            out.put("paperFilename", sess.paperFilename);
        }
        return out;
    }

    @Transactional
    public UUID createContract(UUID sessionId, UUID userId, UUID precedentContractId,
                               Map<String, Object> fieldOverrides) {
        IntakeSession s = sessions.findById(sessionId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Intake session not found"));
        // Already produced a contract that has moved past DRAFT (in review / executed / …): nothing
        // more to do here — return it. While it is still a DRAFT the requester may have gone back to
        // the conversation to change things, so fall through and patch that same draft below.
        if ("SUBMITTED".equals(s.status) && s.resultingContractId != null) {
            boolean stillDraft = contracts.findById(s.resultingContractId)
                    .map(c -> "DRAFT".equals(c.status)).orElse(false);
            if (!stillDraft) return s.resultingContractId;
        }

        Map<String, Object> captured = Json.readMap(s.capturedFields);
        Map<String, String> provenance = strMap(Json.readMap(s.fieldProvenance));
        if (fieldOverrides != null && !fieldOverrides.isEmpty()) {
            FieldCatalog.Normalized n = catalog.normalize(currentType(s, captured), captured, provenance, fieldOverrides, true);
            captured = n.values();
            provenance = n.provenance();
            persist(s, captured, provenance, Json.readMap(s.confidenceScores), Json.readListOfMaps(s.conversationHistory));
        }

        String type = currentType(s, captured);
        if (!catalog.isReady(type, captured, provenance)) {
            List<String> missing = new ArrayList<>();
            for (FieldCatalog.Field f : catalog.forType(type)) {
                if (f.required() && (captured.get(f.key()) == null || String.valueOf(captured.get(f.key())).isBlank()))
                    missing.add(f.label());
            }
            Set<String> unconfirmed = catalog.needsConfirmation(provenance);
            throw new ApiExceptions.BadRequestException(
                    (missing.isEmpty() ? "" : "Still needed: " + String.join(", ", missing) + ". ")
                    + (unconfirmed.isEmpty() ? "" : "Confirm the AI-suggested values first: " + String.join(", ", unconfirmed) + "."));
        }

        UUID entityId = UUID.fromString(String.valueOf(captured.get("contracting_entity_id")));
        Party cp = resolveCounterparty(captured);

        if (precedentContractId == null && captured.get("_precedent_contract_id") != null) {
            try { precedentContractId = UUID.fromString(String.valueOf(captured.get("_precedent_contract_id"))); }
            catch (Exception ignored) {}
        }

        Object mode = captured.get("_start_mode");
        UUID parentId = null;
        String relationship = null;
        Object src = captured.get("_source_contract_id");
        if ("AMEND".equals(String.valueOf(mode)) && src != null) {
            try { parentId = UUID.fromString(String.valueOf(src)); relationship = "AMENDS"; } catch (Exception ignored) {}
        }

        List<String> participantIds = new ArrayList<>();
        Object pv = captured.get("participants");
        if (pv instanceof List<?> l) l.forEach(x -> participantIds.add(String.valueOf(x)));
        else if (pv != null) for (String x : String.valueOf(pv).split("[,;]")) if (!x.isBlank()) participantIds.add(x.trim());

        var req = new ContractService.CreateRequest(
                type,
                blankToNull(captured.get("title")),
                entityId,
                cp == null ? null : cp.id,
                blankToNull(captured.get("counterparty_name")),
                blankToNull(captured.get("governing_law")),
                asInt(captured.get("term_months")),
                typeAttributesFrom(type, captured),
                sessionId,
                precedentContractId,
                "Created from conversational intake session " + shortId(sessionId) + ".",
                parentId,
                relationship,
                participantIds);

        // A reopened session that already produced a contract is a REVISION (requester revising a
        // rejected request) — patch the existing draft rather than creating a second contract.
        boolean revising = s.resultingContractId != null;
        Map<String, Object> contract = revising
                ? contractService.updateFromIntake(s.resultingContractId, req, userId)
                : contractService.create(req, userId);
        UUID contractId = UUID.fromString(String.valueOf(contract.get("id")));

        carryAttachmentsToContract(s, contractId, userId); // mirror session files onto the contract (idempotent)

        if (!revising && s.paperBodyHtml != null && !s.paperBodyHtml.isBlank()) {
            // document is the verbatim third-party paper — mark migrated so it stays read-only
            Contract c = contracts.findById(contractId).orElseThrow();
            c.source = "MIGRATED";
            c.updatedBy = userId;
            contracts.save(c);
        }

        s.status = "SUBMITTED";
        s.resultingContractId = contractId;
        s.updatedAt = Instant.now();
        sessions.save(s);
        audit.record("INTAKE_SESSION", sessionId.toString(), "SUBMITTED", userId, null,
                Map.of("contractId", contractId.toString()));
        return contractId;
    }

    // ------------------------------------------------------------------ helpers

    private IntakeSession requireOpen(UUID sessionId) {
        IntakeSession s = sessions.findById(sessionId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Intake session not found"));
        if (!"OPEN".equals(s.status)) throw new ApiExceptions.BadRequestException("This request has already been submitted.");
        return s;
    }

    private void persist(IntakeSession s, Map<String, Object> captured, Map<String, String> provenance,
                         Map<String, Object> confidence, List<Map<String, Object>> history) {
        s.capturedFields = Json.write(captured);
        s.fieldProvenance = Json.write(provenance);
        s.confidenceScores = Json.write(confidence);
        s.conversationHistory = Json.write(history);
        s.updatedAt = Instant.now();
        s.triageResult = Json.write(computeTriage(currentType(s, captured), captured));
        sessions.save(s);
    }

    private String currentType(IntakeSession s, Map<String, Object> captured) {
        Object v = captured.get("contract_type_code");
        if (v != null && !String.valueOf(v).isBlank()) return String.valueOf(v);
        return s.contractTypeCode;
    }

    private List<String> missingRequiredLabels(String typeCode, Map<String, Object> captured) {
        List<String> out = new ArrayList<>();
        for (FieldCatalog.Field f : catalog.forType(typeCode)) {
            if (f.required() && blankToNull(captured.get(f.key())) == null) out.add(f.key());
        }
        return out;
    }

    private String fieldGuide(String typeCode) {
        StringBuilder sb = new StringBuilder();
        for (FieldCatalog.Field f : catalog.forType(typeCode)) sb.append(fieldLine(f));

        if (typeCode == null) {
            sb.append("\nType-specific fields (also fill these when you know the contract type):\n");
            for (var def : types.findByIsActiveTrue()) {
                List<FieldCatalog.Field> extra = catalog.forType(def.code).stream()
                        .filter(f -> !CORE_KEYS.contains(f.key())).toList();
                if (extra.isEmpty()) continue;
                sb.append("  if ").append(def.code).append(": ");
                sb.append(String.join(", ", extra.stream()
                        .map(f -> f.key() + "(" + f.type() + (f.required() ? ", required" : "") + ")").toList()));
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private static final Set<String> CORE_KEYS = Set.of(
            "contract_type_code", "contracting_entity_id", "counterparty_name", "title", "governing_law", "participants");

    private String fieldLine(FieldCatalog.Field f) {
        StringBuilder sb = new StringBuilder("- ").append(f.key()).append(" (").append(f.type());
        if (f.options() != null && !f.options().isEmpty()) {
            // show a human-readable token for each option (labels for id-valued enums like entity)
            boolean idValued = f.options().get(0).value().length() == 36 && f.options().get(0).value().contains("-");
            sb.append("; allowed: ").append(String.join(" | ", f.options().stream()
                    .map(o -> idValued ? o.label() : o.value()).toList()));
            if (idValued) sb.append("  (output the name; the system resolves it)");
        }
        sb.append(")");
        if (f.required()) sb.append(" [required]");
        if (f.help() != null) sb.append(" — ").append(f.help());
        return sb.append("\n").toString();
    }

    private Map<String, Object> computeTriage(String typeCode, Map<String, Object> captured) {
        ContractTypeDefinition type = typeCode == null ? null : types.findById(typeCode).orElse(null);
        LegalEntity entity = null;
        Object eid = captured.get("contracting_entity_id");
        if (eid != null) {
            try { entity = entities.findById(UUID.fromString(String.valueOf(eid))).orElse(null); }
            catch (Exception ignored) {}
        }
        Party cp = resolveCounterparty(captured);
        Double value = asDouble(firstNonNull(captured.get("value_amount"), captured.get("annual_value")));
        boolean dp = truthy(captured.get("data_processing"));
        Integer term = asInt(captured.get("term_months"));
        String law = blankToNull(captured.get("governing_law"));

        TriageService.Triage tr = triage.score(type, entity, cp, value, dp, term, law, 0);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("score", tr.score());
        m.put("path", tr.path());
        m.put("pathLabel", tr.pathLabel());
        m.put("estimatedBusinessDays", tr.estimatedBusinessDays());
        m.put("factors", tr.factors());
        m.put("autoIssueEligible", tr.autoIssueEligible());
        m.put("explanation", tr.pathLabel() + (tr.estimatedBusinessDays() > 0
                ? ", est. " + tr.estimatedBusinessDays() + " business day(s)" : "") + ".");
        return m;
    }

    private List<Map<String, Object>> suggestPrecedents(String typeCode, Map<String, Object> captured) {
        if (typeCode == null) return List.of();
        UUID entityId = null;
        try {
            Object eid = captured.get("contracting_entity_id");
            if (eid != null) entityId = UUID.fromString(String.valueOf(eid));
        } catch (Exception ignored) {}
        String cpName = blankToNull(captured.get("counterparty_name"));
        return precedents.find(typeCode, entityId, cpName, null, 4).stream().map(m -> Map.<String, Object>of(
                "contractId", m.contractId(),
                "contractNumber", m.contractNumber(),
                "title", m.title(),
                "score", m.score(),
                "reasons", m.reasons(),
                "effectiveDate", m.effectiveDate() == null ? "" : m.effectiveDate(),
                "counterparty", m.counterparty() == null ? "" : m.counterparty())).toList();
    }

    private Party resolveCounterparty(Map<String, Object> captured) {
        String name = blankToNull(captured.get("counterparty_name"));
        if (name == null) return null;
        return parties.findTop10ByLegalNameContainingIgnoreCaseOrTradingNameContainingIgnoreCase(name, name)
                .stream().findFirst().orElse(null);
    }

    private Map<String, Object> typeAttributesFrom(String typeCode, Map<String, Object> captured) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        for (FieldCatalog.Field f : catalog.forType(typeCode)) {
            if (Set.of("contract_type_code", "contracting_entity_id", "counterparty_name", "title", "governing_law", "participants")
                    .contains(f.key())) continue;
            if (captured.get(f.key()) != null) attrs.put(f.key(), captured.get(f.key()));
        }
        return attrs;
    }

    private Map<String, Object> view(IntakeSession s) {
        Map<String, Object> captured = Json.readMap(s.capturedFields);
        Map<String, String> provenance = strMap(Json.readMap(s.fieldProvenance));
        String type = currentType(s, captured);

        // hide internal bookkeeping keys from the client
        Map<String, Object> shown = new LinkedHashMap<>();
        captured.forEach((k, v) -> { if (!k.startsWith("_")) shown.put(k, v); });

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.id);
        m.put("status", s.status);
        m.put("requestNumber", s.requestNumber);
        m.put("channel", s.channel);
        m.put("contractType", type);
        m.put("startMode", captured.get("_start_mode"));
        m.put("sourceContractId", captured.get("_source_contract_id"));
        m.put("capturedFields", shown);
        m.put("fieldProvenance", provenance);
        m.put("confidenceScores", Json.readMap(s.confidenceScores));
        m.put("fieldSpec", catalog.forType(type));
        m.put("needsConfirmation", catalog.needsConfirmation(provenance));
        m.put("readyToSubmit", catalog.isReady(type, captured, provenance));
        m.put("conversation", Json.readListOfMaps(s.conversationHistory));
        m.put("triage", s.triageResult == null ? null : Json.readMap(s.triageResult));
        m.put("resultingContractId", s.resultingContractId);
        m.put("saved", s.saved);
        m.put("paperFilename", s.paperFilename);
        m.put("modelLive", ai.modelIsLive());
        return m;
    }

    private String renderHistory(List<Map<String, Object>> history) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> h : history) sb.append(h.get("role")).append(": ").append(h.get("content")).append("\n");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> strMap(Map<String, Object> in) {
        Map<String, String> out = new LinkedHashMap<>();
        in.forEach((k, v) -> out.put(k, v == null ? null : String.valueOf(v)));
        return out;
    }

    private static boolean truthy(Object o) {
        if (o instanceof Boolean b) return b;
        return o != null && Set.of("true", "yes", "1").contains(String.valueOf(o).toLowerCase());
    }

    private static String firstName(String display) {
        if (display == null || display.isBlank()) return "there";
        return display.split("[ (]")[0];
    }

    private static String blankToNull(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    private static Object firstNonNull(Object a, Object b) { return a != null ? a : b; }

    private static Double asDouble(Object o) {
        if (o == null) return null;
        try { return Double.parseDouble(String.valueOf(o).replaceAll("[^0-9.\\-]", "")); } catch (Exception e) { return null; }
    }

    private static Integer asInt(Object o) {
        Double d = asDouble(o);
        return d == null ? null : (int) Math.round(d);
    }

    private static String shortId(UUID id) { return id.toString().substring(0, 8); }
}
