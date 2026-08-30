package com.acme.clm.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clause_variant")
public class ClauseVariant {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @Column(name = "clause_concept_id", nullable = false)
    public UUID clauseConceptId;
    @Column(name = "version_no")
    public int versionNo = 1;
    @Column(name = "jurisdiction_code")
    public String jurisdictionCode = "GLOBAL";
    @Column(name = "language_code")
    public String languageCode = "en";
    @Column(name = "body_text", nullable = false, columnDefinition = "text")
    public String bodyText;
    @Column(name = "position_tier")
    public String positionTier = "ACCEPTABLE";
    @Column(name = "risk_tier")
    public String riskTier = "MEDIUM";
    @Column(name = "guidance_notes", columnDefinition = "text")
    public String guidanceNotes;
    @Column(name = "canonical_source_variant_id")
    public UUID canonicalSourceVariantId;
    @Column(name = "translation_status")
    public String translationStatus = "CURRENT";
    @Column(name = "approved_by")
    public UUID approvedBy;
    @Column(name = "approved_at")
    public Instant approvedAt;
    public String status = "ACTIVE";
}
