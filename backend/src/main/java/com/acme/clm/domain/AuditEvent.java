package com.acme.clm.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_event")
public class AuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @Column(name = "entity_type", nullable = false)
    public String entityType;
    @Column(name = "entity_id", nullable = false)
    public String entityId;
    @Column(nullable = false)
    public String action;
    @Column(name = "actor_user_id")
    public UUID actorUserId;
    @Column(name = "actor_type")
    public String actorType = "USER";
    @Column(name = "occurred_at")
    public Instant occurredAt = Instant.now();
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state")
    public String beforeState;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state")
    public String afterState;
    @Column(name = "ai_model_id")
    public String aiModelId;
    @Column(name = "ai_prompt_hash")
    public String aiPromptHash;
    @Column(name = "correlation_id")
    public String correlationId;
}
