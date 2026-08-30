package com.acme.clm.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "comment_thread")
public class CommentThread {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @Column(name = "entity_type", nullable = false)
    public String entityType;
    @Column(name = "entity_id", nullable = false)
    public String entityId;
    @Column(name = "subject_ref")
    public String subjectRef;
    public String title;
    public String status = "OPEN";
    @Column(name = "created_by", nullable = false)
    public UUID createdBy;
    @Column(name = "created_at")
    public Instant createdAt = Instant.now();
    @Column(name = "resolved_by")
    public UUID resolvedBy;
    @Column(name = "resolved_at")
    public Instant resolvedAt;
}
