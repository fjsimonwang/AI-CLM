package com.acme.clm.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "legal_entity")
public class LegalEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @Column(name = "legal_name", nullable = false)
    public String legalName;
    @Column(name = "short_name", nullable = false)
    public String shortName;
    @Column(name = "country_code", nullable = false)
    public String countryCode;
    @Column(name = "registration_number")
    public String registrationNumber;
    @Column(name = "default_governing_law")
    public String defaultGoverningLaw;
    @Column(name = "default_language")
    public String defaultLanguage = "en";
    @Column(name = "parent_entity_id")
    public UUID parentEntityId;
    @Column(name = "data_residency_region")
    public String dataResidencyRegion = "GLOBAL";
    @Column(name = "legal_team_id")
    public UUID legalTeamId;
    public String status = "ACTIVE";
}
