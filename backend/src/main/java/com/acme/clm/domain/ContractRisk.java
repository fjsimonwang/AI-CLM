package com.acme.clm.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contract_risk")
public class ContractRisk {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(name = "contract_id", nullable = false)
    public UUID contractId;

    @Column(nullable = false, columnDefinition = "text")
    public String title;

    public String category;

    @Column(nullable = false)
    public String severity = "MEDIUM";

    @Column(columnDefinition = "text")
    public String detail;

    @Column(columnDefinition = "text")
    public String location;

    /** MANUAL | AI_REVIEW */
    @Column(nullable = false)
    public String source = "MANUAL";

    @Column(name = "dedupe_key")
    public String dedupeKey;

    /** OPEN | CLOSED */
    @Column(nullable = false)
    public String status = "OPEN";

    /** While CLOSED: RESOLVED | DISMISSED */
    public String resolution;

    @Column(name = "created_by")
    public UUID createdBy;

    @Column(name = "closed_by")
    public UUID closedBy;

    @Column(name = "closed_at")
    public Instant closedAt;

    @Column(name = "created_at")
    public Instant createdAt = Instant.now();
}