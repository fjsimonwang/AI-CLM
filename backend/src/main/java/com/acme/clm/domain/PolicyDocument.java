package com.acme.clm.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * A CLM policy or procedure document the help assistant is grounded in. Its audience is defined
 * by access dimensions: {@code roles}, {@code countries}, {@code regions} (each a comma-separated
 * list; empty means "everyone on that dimension") plus a {@code confidential} flag that further
 * limits it to broad-access users. The help chat only sees documents the asking user matches.
 */
@Entity
@Table(name = "policy_document")
public class PolicyDocument {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(nullable = false)
    public String title;

    @Column(name = "body_html", columnDefinition = "text", nullable = false)
    public String bodyHtml = "";

    @Column(nullable = false)
    public String roles = "";

    @Column(nullable = false)
    public String countries = "";

    @Column(nullable = false)
    public String regions = "";

    @Column(nullable = false)
    public boolean confidential = false;

    @Column(name = "is_active", nullable = false)
    public boolean isActive = true;

    @Column(name = "uploaded_by")
    public UUID uploadedBy;

    @Column(name = "created_at")
    public Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    public Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() { updatedAt = Instant.now(); }
}
