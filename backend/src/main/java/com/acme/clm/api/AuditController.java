package com.acme.clm.api;

import com.acme.clm.common.Json;
import com.acme.clm.domain.AuditEvent;
import com.acme.clm.repo.Repos;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final Repos.AuditEvents events;
    private final Repos.Users users;

    public AuditController(Repos.AuditEvents events, Repos.Users users) {
        this.events = events;
        this.users = users;
    }

    @GetMapping
    public List<Map<String, Object>> recent() {
        return events.findTop100ByOrderByOccurredAtDesc().stream().map(this::map).toList();
    }

    @GetMapping("/{entityType}/{entityId}")
    public List<Map<String, Object>> forEntity(@PathVariable String entityType, @PathVariable String entityId) {
        return events.findByEntityTypeAndEntityIdOrderByOccurredAtDesc(entityType.toUpperCase(), entityId)
                .stream().map(this::map).toList();
    }

    private Map<String, Object> map(AuditEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.id);
        m.put("entityType", e.entityType);
        m.put("entityId", e.entityId);
        m.put("action", e.action);
        m.put("actorType", e.actorType);
        m.put("actor", e.actorUserId == null ? (e.actorType == null ? "system" : e.actorType.toLowerCase())
                : users.findById(e.actorUserId).map(u -> u.displayName).orElse("unknown"));
        m.put("occurredAt", e.occurredAt);
        m.put("aiModelId", e.aiModelId);
        m.put("before", e.beforeState == null ? null : Json.read(e.beforeState));
        m.put("after", e.afterState == null ? null : Json.read(e.afterState));
        return m;
    }
}
