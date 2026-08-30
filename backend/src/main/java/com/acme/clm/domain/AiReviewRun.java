package com.acme.clm.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_review_run")
public class AiReviewRun {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(name = "contract_id", nullable = false)
    public UUID contractId;

    @Column(name = "requested_by")
    public UUID requestedBy;

    @Column(nullable = false)
    public String status = "RUNNING";

    public String overall;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public String findings;

    @Column(name = "model_id")
    public String modelId;

    @Column(columnDefinition = "text")
    public String error;

    @Column(name = "created_at")
    public Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    public Instant completedAt;
}