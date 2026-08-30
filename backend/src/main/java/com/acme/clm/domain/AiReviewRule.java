package com.acme.clm.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_review_rule")
public class AiReviewRule {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(nullable = false, unique = true)
    public String code;

    @Column(nullable = false)
    public String label;

    @Column(nullable = false, columnDefinition = "text")
    public String instruction;

    @Column(nullable = false)
    public String severity = "HIGH";

    @Column(name = "is_active", nullable = false)
    public boolean isActive = true;

    @Column(nullable = false)
    public int sort = 0;

    @Column(name = "created_by")
    public UUID createdBy;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}