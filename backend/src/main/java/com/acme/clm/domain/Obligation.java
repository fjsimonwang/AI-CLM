package com.acme.clm.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "obligation")
public class Obligation {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @Column(name = "contract_id", nullable = false)
    public UUID contractId;
    @Column(name = "obligation_type")
    public String obligationType = "DELIVERABLE";
    @Column(nullable = false, columnDefinition = "text")
    public String description;
    @Column(name = "due_date")
    public LocalDate dueDate;
    @Column(name = "recurrence_rule")
    public String recurrenceRule;
    @Column(name = "owner_user_id")
    public UUID ownerUserId;
    @Column(name = "owning_department")
    public String owningDepartment;
    public String status = "OPEN";
    @Column(name = "source_clause_variant_id")
    public UUID sourceClauseVariantId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_text_location")
    public String sourceTextLocation;
    @Column(name = "extraction_confidence")
    public BigDecimal extractionConfidence;
    @Column(name = "verified_by")
    public UUID verifiedBy;
    @Column(name = "alert_lead_days")
    public int alertLeadDays = 30;
    @Column(name = "created_at")
    public Instant createdAt = Instant.now();
}
