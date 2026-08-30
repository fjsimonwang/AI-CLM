package com.acme.clm.service;

import com.acme.clm.common.Json;
import com.acme.clm.domain.AuditEvent;
import com.acme.clm.repo.Repos;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final Repos.AuditEvents repo;

    public AuditService(Repos.AuditEvents repo) { this.repo = repo; }

    public void record(String entityType, String entityId, String action, UUID actor,
                       Object before, Object after) {
        AuditEvent e = new AuditEvent();
        e.entityType = entityType;
        e.entityId = entityId;
        e.action = action;
        e.actorUserId = actor;
        e.actorType = actor == null ? "SYSTEM" : "USER";
        e.beforeState = before == null ? null : Json.write(before);
        e.afterState = after == null ? null : Json.write(after);
        e.correlationId = UUID.randomUUID().toString();
        repo.save(e);
    }

    public void recordAi(String entityType, String entityId, String action, UUID actor,
                         String aiModelId, String promptHash) {
        AuditEvent e = new AuditEvent();
        e.entityType = entityType;
        e.entityId = entityId;
        e.action = action;
        e.actorUserId = actor;
        e.actorType = "AI";
        e.aiModelId = aiModelId;
        e.aiPromptHash = promptHash;
        e.correlationId = UUID.randomUUID().toString();
        repo.save(e);
    }
}
