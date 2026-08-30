package com.acme.clm.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "playbook")
public class Playbook {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(nullable = false)
    public String name;

    @Column(name = "contract_type_code")
    public String contractTypeCode;

    @Column(name = "legal_entity_id")
    public UUID legalEntityId;

    public String jurisdiction;

    public String language = "EN";

    public String description;

    @Column(name = "body_html", columnDefinition = "text")
    public String bodyHtml = "";

    @Column(name = "is_active", nullable = false)
    public boolean isActive = true;

    @Column(name = "created_by")
    public UUID createdBy;

    @Column(name = "created_at")
    public Instant createdAt = Instant.now();
}