package com.acme.clm.api;

import com.acme.clm.common.ApiExceptions;
import com.acme.clm.common.HtmlSanitizer;
import com.acme.clm.config.CurrentUser;
import com.acme.clm.domain.CommentMessage;
import com.acme.clm.domain.CommentThread;
import com.acme.clm.repo.Repos;
import com.acme.clm.service.AuditService;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Threaded, multi-participant, rich-text discussion on contracts and approval tasks. */
@RestController
@RequestMapping("/api/comments")
@PreAuthorize("hasAuthority('PERM_COMMENT')")
public class CommentController {

    private final Repos.CommentThreads threads;
    private final Repos.CommentMessages messages;
    private final Repos.Users users;
    private final HtmlSanitizer sanitizer;
    private final AuditService audit;
    private final CurrentUser current;

    public CommentController(Repos.CommentThreads threads, Repos.CommentMessages messages, Repos.Users users,
                             HtmlSanitizer sanitizer, AuditService audit, CurrentUser current) {
        this.threads = threads;
        this.messages = messages;
        this.users = users;
        this.sanitizer = sanitizer;
        this.audit = audit;
        this.current = current;
    }

    @GetMapping("/{entityType}/{entityId}")
    public List<Map<String, Object>> forEntity(@PathVariable String entityType, @PathVariable String entityId) {
        return threads.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType.toUpperCase(), entityId)
                .stream().map(this::threadView).toList();
    }

    public record NewThread(String entityType, String entityId, String title,
                            @NotBlank String bodyHtml, String subjectRef) {}

    @PostMapping("/threads")
    public Map<String, Object> createThread(@RequestBody NewThread req) {
        CommentThread t = new CommentThread();
        t.entityType = req.entityType().toUpperCase();
        t.entityId = req.entityId();
        t.title = req.title() == null || req.title().isBlank() ? "Comment" : req.title().trim();
        t.subjectRef = req.subjectRef();
        t.createdBy = current.id();
        threads.save(t);
        addMessage(t.id, req.bodyHtml());
        audit.record(t.entityType, t.entityId, "COMMENT_THREAD_OPENED", current.id(), null,
                Map.of("title", t.title));
        return threadView(t);
    }

    public record Reply(@NotBlank String bodyHtml) {}

    @PostMapping("/threads/{threadId}/reply")
    public Map<String, Object> reply(@PathVariable UUID threadId, @RequestBody Reply req) {
        CommentThread t = threads.findById(threadId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Thread not found"));
        addMessage(threadId, req.bodyHtml());
        audit.record(t.entityType, t.entityId, "COMMENT_ADDED", current.id(), null, null);
        return threadView(t);
    }

    @PostMapping("/threads/{threadId}/resolve")
    public Map<String, Object> resolve(@PathVariable UUID threadId, @RequestParam(defaultValue = "true") boolean resolved) {
        CommentThread t = threads.findById(threadId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Thread not found"));
        t.status = resolved ? "RESOLVED" : "OPEN";
        t.resolvedBy = resolved ? current.id() : null;
        t.resolvedAt = resolved ? Instant.now() : null;
        threads.save(t);
        audit.record(t.entityType, t.entityId, resolved ? "COMMENT_THREAD_RESOLVED" : "COMMENT_THREAD_REOPENED",
                current.id(), null, null);
        return threadView(t);
    }

    @PatchMapping("/messages/{messageId}")
    public Map<String, Object> edit(@PathVariable UUID messageId, @RequestBody Reply req) {
        CommentMessage m = messages.findById(messageId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Message not found"));
        if (!m.authorUserId.equals(current.id())) throw new ApiExceptions.ForbiddenException("Not your comment");
        m.bodyHtml = sanitizer.clean(req.bodyHtml());
        m.editedAt = Instant.now();
        messages.save(m);
        return threadView(threads.findById(m.threadId).orElseThrow());
    }

    @DeleteMapping("/messages/{messageId}")
    public Map<String, Object> softDelete(@PathVariable UUID messageId) {
        CommentMessage m = messages.findById(messageId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Message not found"));
        if (!m.authorUserId.equals(current.id())) throw new ApiExceptions.ForbiddenException("Not your comment");
        m.deleted = true;
        messages.save(m);
        return threadView(threads.findById(m.threadId).orElseThrow());
    }

    private void addMessage(UUID threadId, String bodyHtml) {
        String clean = sanitizer.clean(bodyHtml);
        if (sanitizer.plainText(clean).isBlank()) throw new ApiExceptions.BadRequestException("Comment is empty");
        CommentMessage m = new CommentMessage();
        m.threadId = threadId;
        m.authorUserId = current.id();
        m.bodyHtml = clean;
        messages.save(m);
    }

    private Map<String, Object> threadView(CommentThread t) {
        List<Map<String, Object>> msgs = messages.findByThreadIdOrderByCreatedAtAsc(t.id).stream().map(m -> {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("id", m.id);
            mm.put("author", userName(m.authorUserId));
            mm.put("authorId", m.authorUserId);
            mm.put("bodyHtml", m.deleted ? "<p><em>(comment deleted)</em></p>" : m.bodyHtml);
            mm.put("createdAt", m.createdAt);
            mm.put("editedAt", m.editedAt);
            mm.put("deleted", m.deleted);
            mm.put("mine", m.authorUserId.equals(safeCurrent()));
            return mm;
        }).toList();
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", t.id);
        v.put("title", t.title);
        v.put("status", t.status);
        v.put("subjectRef", t.subjectRef);
        v.put("openedBy", userName(t.createdBy));
        v.put("createdAt", t.createdAt);
        v.put("resolvedBy", t.resolvedBy == null ? null : userName(t.resolvedBy));
        v.put("participants", msgs.stream().map(m -> m.get("author")).distinct().toList());
        v.put("messages", msgs);
        return v;
    }

    private UUID safeCurrent() {
        try { return current.id(); } catch (Exception e) { return null; }
    }

    private String userName(UUID id) {
        return id == null ? null : users.findById(id).map(u -> u.displayName).orElse("unknown");
    }
}
