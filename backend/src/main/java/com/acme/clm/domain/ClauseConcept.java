package com.acme.clm.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "clause_concept")
public class ClauseConcept {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @Column(name = "concept_code", nullable = false, unique = true)
    public String conceptCode;
    public String name;
    public String category;
    @Column(columnDefinition = "text")
    public String description;
    @Column(name = "risk_category")
    public String riskCategory;
    @Column(name = "is_core")
    public boolean isCore = false;
    @Column(name = "owning_legal_team_id")
    public UUID owningLegalTeamId;
}
