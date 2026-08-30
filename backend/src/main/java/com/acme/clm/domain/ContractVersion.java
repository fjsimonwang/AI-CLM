package com.acme.clm.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contract_version")
public class ContractVersion {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @Column(name = "contract_id", nullable = false)
    public UUID contractId;
    @Column(name = "version_no", nullable = false)
    public int versionNo;
    @Column(name = "version_label")
    public String versionLabel;
    @Column(name = "change_summary", columnDefinition = "text")
    public String changeSummary;
    @Column(name = "body_text", columnDefinition = "text")
    public String bodyText;
    @Column(name = "is_executed")
    public boolean isExecuted = false;
    @Column(name = "created_by")
    public UUID createdBy;
    @Column(name = "created_at")
    public Instant createdAt = Instant.now();
}
