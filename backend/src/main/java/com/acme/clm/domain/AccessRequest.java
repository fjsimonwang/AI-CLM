package com.acme.clm.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "access_request")
public class AccessRequest {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @Column(name = "user_id", nullable = false)
    public UUID userId;
    @JdbcTypeCode(SqlTypes.JSON)
    public String constraints = "{}";
    @Column(columnDefinition = "text")
    public String justification;
    public String status = "PENDING";
    @Column(name = "created_at")
    public Instant createdAt = Instant.now();
    @Column(name = "decided_by")
    public UUID decidedBy;
    @Column(name = "decided_at")
    public Instant decidedAt;
    @Column(name = "decision_note", columnDefinition = "text")
    public String decisionNote;
    @Column(name = "expires_days")
    public Integer expiresDays;
}
