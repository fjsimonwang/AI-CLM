package com.acme.clm.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "precedent_link")
public class PrecedentLink {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @Column(name = "contract_id", nullable = false)
    public UUID contractId;
    @Column(name = "precedent_contract_id", nullable = false)
    public UUID precedentContractId;
    @Column(name = "match_score", nullable = false)
    public BigDecimal matchScore;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "match_reasons")
    public String matchReasons = "[]";
    @Column(name = "used_for")
    public String usedFor = "PREFILL";
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fields_inherited")
    public String fieldsInherited;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "clauses_inherited")
    public String clausesInherited;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "deviations_carried")
    public String deviationsCarried;
    @Column(name = "acknowledged_by")
    public UUID acknowledgedBy;
    @Column(name = "acknowledged_at")
    public Instant acknowledgedAt;
    @Column(name = "created_at")
    public Instant createdAt = Instant.now();
}
