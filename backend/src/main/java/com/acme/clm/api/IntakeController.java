package com.acme.clm.api;

import com.acme.clm.common.Json;
import com.acme.clm.config.CurrentUser;
import com.acme.clm.service.IntakeService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/intake")
public class IntakeController {

    private final IntakeService service;
    private final CurrentUser current;
    private final ExecutorService sse = Executors.newCachedThreadPool();

    public IntakeController(IntakeService service, CurrentUser current) {
        this.service = service;
        this.current = current;
    }

    @PostMapping("/sessions")
    public Map<String, Object> create() { return service.create(current.id()); }

    @GetMapping("/sessions")
    public Object listMine() { return service.listMine(current.id()); }

    @GetMapping("/sessions/{id}")
    public Map<String, Object> get(@PathVariable UUID id) { return service.get(id); }

    @DeleteMapping("/sessions/{id}")
    public void delete(@PathVariable UUID id) { service.delete(id, current.id()); }

    /** Explicitly saves the draft so it appears in the user's saved-draft list. */
    @PostMapping("/sessions/{id}/save")
    public Map<String, Object> save(@PathVariable UUID id) { return service.saveDraft(id, current.id()); }

    public record StartFromRequest(UUID contractId, String mode) {}

    /** Seed the session from a contract the user picked: mode = SIMILAR | SAME_PARTY | AMEND. */
    @PostMapping("/sessions/{id}/start-from")
    public Map<String, Object> startFrom(@PathVariable UUID id, @RequestBody StartFromRequest req) {
        return service.startFrom(id, current.id(), req.contractId(), req.mode());
    }

    public record MessageRequest(String message) {}

    @PostMapping("/sessions/{id}/message")
    public Map<String, Object> message(@PathVariable UUID id, @RequestBody MessageRequest req) {
        return service.turn(id, current.id(), req.message());
    }

    /** Streamed variant (plan §3: do not build AI interaction on request-response polling). */
    @PostMapping(value = "/sessions/{id}/message/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter messageStream(@PathVariable UUID id, @RequestBody MessageRequest req) {
        UUID userId = current.id();
        SseEmitter emitter = new SseEmitter(120_000L);
        sse.submit(() -> {
            try {
                emitter.send(SseEmitter.event().name("status").data("Thinking…"));
                Map<String, Object> result = service.turn(id, userId, req.message());
                String reply = String.valueOf(result.getOrDefault("assistantReply", ""));
                for (String chunk : chunk(reply)) {
                    emitter.send(SseEmitter.event().name("token").data(chunk));
                    Thread.sleep(18);
                }
                emitter.send(SseEmitter.event().name("complete").data(Json.write(result)));
                emitter.complete();
            } catch (Exception e) {
                try { emitter.send(SseEmitter.event().name("error").data(e.getMessage())); } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    /** Upload the counterparty's paper contract: AI extracts the fields, the paper becomes the document. */
    @PostMapping(value = "/sessions/{id}/upload-paper", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadPaper(@PathVariable UUID id,
                                           @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return service.uploadPaper(id, current.id(), file);
    }

    /** Preview the uploaded third-party paper before submitting. */
    @GetMapping("/sessions/{id}/paper")
    public org.springframework.http.ResponseEntity<byte[]> paper(@PathVariable UUID id) {
        String html = service.paperHtml(id);
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Security-Policy", "sandbox") // uploaded paper is untrusted HTML
                .contentType(org.springframework.http.MediaType.TEXT_HTML)
                .body(html.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public record StartTypeRequest(String type) {}

    /** Quick-start with a frequently-used contract type (no LLM call). */
    @PostMapping("/sessions/{id}/start-type")
    public Map<String, Object> startType(@PathVariable UUID id, @RequestBody StartTypeRequest req) {
        return service.startType(id, current.id(), req.type());
    }

    // ---- supporting attachments ----

    @PostMapping(value = "/sessions/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> addAttachment(@PathVariable UUID id,
                                             @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return service.addAttachment(id, current.id(), file);
    }

    @GetMapping("/sessions/{id}/attachments")
    public List<Map<String, Object>> listAttachments(@PathVariable UUID id) {
        return service.listAttachments(id);
    }

    @DeleteMapping("/sessions/{id}/attachments/{attachmentId}")
    public Map<String, Object> removeAttachment(@PathVariable UUID id, @PathVariable UUID attachmentId) {
        return service.removeAttachment(id, attachmentId, current.id());
    }

    @GetMapping("/sessions/{id}/attachments/{attachmentId}/file")
    public org.springframework.http.ResponseEntity<byte[]> attachmentFile(@PathVariable UUID id,
                                                                          @PathVariable UUID attachmentId) {
        var a = service.attachmentFile(id, attachmentId);
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename*=UTF-8''" + urlEncode(a.filename))
                .header("Content-Type", a.contentType == null ? "application/octet-stream" : a.contentType)
                .body(a.content);
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s == null ? "attachment.bin" : s, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    public record FieldsRequest(Map<String, Object> fields, List<String> confirm) {}

    /** Direct edits / confirmations from the structured panel (plan §6A.1: form and chat are one artifact). */
    @PutMapping("/sessions/{id}/fields")
    public Map<String, Object> updateFields(@PathVariable UUID id, @RequestBody FieldsRequest req) {
        return service.updateFields(id, current.id(),
                req == null ? null : req.fields(), req == null ? null : req.confirm());
    }

    public record SubmitRequest(UUID precedentContractId, Map<String, Object> fields) {}

    @PostMapping("/sessions/{id}/submit")
    public Map<String, Object> submit(@PathVariable UUID id, @RequestBody(required = false) SubmitRequest req) {
        return service.submit(id, current.id(),
                req == null ? null : req.precedentContractId(),
                req == null ? null : req.fields());
    }

    private static java.util.List<String> chunk(String s) {
        java.util.List<String> out = new java.util.ArrayList<>();
        String[] words = s.split(" ");
        StringBuilder cur = new StringBuilder();
        for (String w : words) {
            cur.append(w).append(" ");
            if (cur.length() > 12) { out.add(cur.toString()); cur.setLength(0); }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }
}
