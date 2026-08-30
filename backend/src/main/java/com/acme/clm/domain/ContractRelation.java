package com.acme.clm.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contract_relation")
public class ContractRelation {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @Column(name = "contract_id", nullable = false)
    public UUID contractId;
    @Column(name = "related_contract_id", nullable = false)
    public UUID relatedContractId;
    @Column(name = "relation_type", nullable = false)
    public String relationType;
    @Column(name = "source", nullable = false)
    public String source = "AI";
    @Column(name = "status", nullable = false)
    public String status = "SUGGESTED";
    @Column(name = "confidence")
    public Double confidence;
    @Column(name = "reasons")
    public String reasons;
    @Column(name = "created_by")
    public UUID createdBy;
    @Column(name = "created_at")
    public Instant createdAt = Instant.now();
    @Column(name = "decided_by")
    public UUID decidedBy;
    @Column(name = "decided_at")
    public Instant decidedAt;
}