package com.acme.clm.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "contract")
public class Contract {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(name = "contract_number", nullable = false, unique = true)
    public String contractNumber;

    @Column(name = "contract_type_code", nullable = false)
    public String contractTypeCode;

    public String title;
    public String status = "DRAFT";

    @Column(name = "contracting_entity_id", nullable = false)
    public UUID contractingEntityId;

    @Column(name = "parent_contract_id")
    public UUID parentContractId;

    @Column(name = "relationship_type")
    public String relationshipType;

    @Column(name = "template_id")
    public UUID templateId;

    @Column(name = "governing_law_code")
    public String governingLawCode;

    @Column(name = "dispute_resolution_forum")
    public String disputeResolutionForum;

    @Column(name = "primary_language")
    public String primaryLanguage = "en";

    @Column(name = "prevailing_language")
    public String prevailingLanguage;

    @Column(name = "effective_date")
    public LocalDate effectiveDate;

    @Column(name = "expiry_date")
    public LocalDate expiryDate;

    @Column(name = "notice_period_days")
    public Integer noticePeriodDays;

    @Column(name = "auto_renew")
    public boolean autoRenew;

    @Column(name = "renewal_term_months")
    public Integer renewalTermMonths;

    @Column(name = "value_amount")
    public BigDecimal valueAmount;

    public String currency;

    @Column(name = "value_basis")
    public String valueBasis;

    @Column(name = "risk_score")
    public Integer riskScore;

    @Column(name = "risk_tier")
    public String riskTier;

    public String source = "NATIVE";

    @Column(name = "confidentiality_level")
    public String confidentialityLevel = "INTERNAL";

    @Column(name = "owner_user_id")
    public UUID ownerUserId;

    @Column(name = "assigned_lawyer_id")
    public UUID assignedLawyerId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "type_attributes")
    public String typeAttributes = "{}";

    @Column(columnDefinition = "text")
    public String summary;

    @Column(name = "intake_session_id")
    public UUID intakeSessionId;

    @Column(name = "annual_value_amount")
    public BigDecimal annualValueAmount;

    @Column(name = "payment_terms_days")
    public Integer paymentTermsDays;

    @Column(name = "renewal_type")
    public String renewalType;

    @Column(name = "liability_summary", columnDefinition = "text")
    public String liabilitySummary;

    @Column(name = "editor_document_id")
    public String editorDocumentId;

    @Column(name = "agent_collab_enabled", nullable = false)
    public boolean agentCollabEnabled = false;

    @Column(name = "created_at")
    public Instant createdAt = Instant.now();

    @Column(name = "created_by")
    public UUID createdBy;

    @Column(name = "updated_at")
    public Instant updatedAt = Instant.now();

    @Column(name = "updated_by")
    public UUID updatedBy;

    /** Keep updated_at current on every persisted change, regardless of the call site. */
    @PreUpdate
    void touchUpdatedAt() {
        updatedAt = Instant.now();
    }
}
