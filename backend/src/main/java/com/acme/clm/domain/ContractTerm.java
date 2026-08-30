package com.acme.clm.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "contract_term")
public class ContractTerm {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @Column(name = "contract_id", nullable = false)
    public UUID contractId;
    @Column(name = "term_key", nullable = false)
    public String termKey;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "term_value", nullable = false)
    public String termValue;
    @Column(name = "term_type")
    public String termType = "STRING";
    @Column(name = "is_inherited")
    public boolean isInherited = false;
    @Column(name = "source_contract_id")
    public UUID sourceContractId;
    @Column(name = "effective_from")
    public LocalDate effectiveFrom;
    @Column(name = "effective_to")
    public LocalDate effectiveTo;
    @Column(name = "extraction_confidence")
    public BigDecimal extractionConfidence;
    @Column(name = "verified_by")
    public UUID verifiedBy;
    @Column(name = "verified_at")
    public Instant verifiedAt;
}
