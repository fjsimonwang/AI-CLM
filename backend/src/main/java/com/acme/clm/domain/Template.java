package com.acme.clm.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "template")
public class Template {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    public String name;
    @Column(name = "contract_type_code", nullable = false)
    public String contractTypeCode;
    @Column(name = "legal_entity_id")
    public UUID legalEntityId;
    @Column(name = "jurisdiction_code")
    public String jurisdictionCode = "GLOBAL";
    @Column(name = "language_code")
    public String languageCode = "en";
    @Column(name = "version_no")
    public int versionNo = 1;
    public String status = "ACTIVE";
    @Column(name = "owning_legal_team_id")
    public UUID owningLegalTeamId;
    @Column(name = "approved_by")
    public UUID approvedBy;
    @Column(name = "approved_at")
    public Instant approvedAt;
    @Column(name = "body_html", columnDefinition = "text")
    public String bodyHtml;
    @Column(columnDefinition = "text")
    public String description;
    public String tags;
}
