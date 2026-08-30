package com.acme.clm.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "intake_attachment")
public class IntakeAttachment {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @Column(name = "intake_session_id", nullable = false)
    public UUID intakeSessionId;
    @Column(nullable = false)
    public String filename;
    @Column(name = "content_type")
    public String contentType;
    @Column(name = "size_bytes", nullable = false)
    public long sizeBytes;
    @Column(nullable = false)
    public byte[] content;
    @Column(name = "created_at")
    public Instant createdAt = Instant.now();
}