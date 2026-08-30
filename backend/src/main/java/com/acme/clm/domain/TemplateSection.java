package com.acme.clm.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "template_section")
public class TemplateSection {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @Column(name = "template_id", nullable = false)
    public UUID templateId;
    @Column(name = "sort_order", nullable = false)
    public int sortOrder;
    public String heading;
    @Column(name = "is_optional")
    public boolean isOptional = false;
    @Column(name = "inclusion_condition")
    public String inclusionCondition;
    @Column(name = "clause_concept_id")
    public UUID clauseConceptId;
    @Column(name = "default_clause_variant_id")
    public UUID defaultClauseVariantId;
    @Column(name = "static_body", columnDefinition = "text")
    public String staticBody;
}
