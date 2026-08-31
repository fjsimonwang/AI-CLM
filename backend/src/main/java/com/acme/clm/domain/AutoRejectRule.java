package com.acme.clm.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** An approver's automatic-rejection rule: scope, natural-language instructions and the AI-structured executable form. */
@Entity
@Table(name = "auto_reject_rule")
public class AutoRejectRule {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(name = "owner_user_id", nullable = false)
    public UUID ownerUserId;

    @Column(nullable = false)
    public String name;

    @Column(nullable = false)
    public boolean enabled = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    public String scope = "{}";

    @Column(nullable = false, columnDefinition = "text")
    public String instructions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "text")
    public String structured;

    @Column(name = "interpretation_model")
    public String interpretationModel;

    @Column(name = "interpreted_at")
    public Instant interpretedAt;

    @Column(name = "fired_count", nullable = false)
    public int firedCount = 0;

    @Column(name = "created_at")
    public Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    public Instant updatedAt = Instant.now();
}