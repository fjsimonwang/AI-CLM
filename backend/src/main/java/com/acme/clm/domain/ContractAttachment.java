package com.acme.clm.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contract_attachment")
public class ContractAttachment {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @Column(name = "contract_id", nullable = false)
    public UUID contractId;
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