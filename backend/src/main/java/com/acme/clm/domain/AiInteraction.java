package com.acme.clm.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_interaction")
public class AiInteraction {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @Column(name = "contract_id")
    public UUID contractId;
    @Column(name = "intake_session_id")
    public UUID intakeSessionId;
    @Column(name = "user_id")
    public UUID userId;
    @Column(nullable = false)
    public String surface;
    @Column(nullable = false)
    public String capability;
    @Column(name = "model_id")
    public String modelId;
    @Column(name = "prompt_id")
    public String promptId;
    @Column(name = "prompt_version")
    public String promptVersion;
    @Column(name = "input_hash")
    public String inputHash;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_summary")
    public String inputSummary;
    @JdbcTypeCode(SqlTypes.JSON)
    public String output;
    @Column(name = "confidence_score")
    public BigDecimal confidenceScore;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_references")
    public String sourceReferences;
    public String outcome = "PENDING";
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "edited_delta")
    public String editedDelta;
    @Column(name = "latency_ms")
    public Integer latencyMs;
    @Column(name = "token_cost")
    public Integer tokenCost;
    @Column(name = "occurred_at")
    public Instant occurredAt = Instant.now();
    @Column(name = "correlation_id")
    public String correlationId;
    @Column(name = "reverted_at")
    public Instant revertedAt;
    @Column(name = "reverted_by")
    public UUID revertedBy;
}
