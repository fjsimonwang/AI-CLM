package com.acme.clm.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "contract_briefing")
public class ContractBriefing {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(name = "contract_id", nullable = false, unique = true)
    public UUID contractId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public String briefing;

    public String model;

    @Column(name = "created_at")
    public Instant createdAt = Instant.now();

    @Column(name = "created_by")
    public UUID createdBy;
}