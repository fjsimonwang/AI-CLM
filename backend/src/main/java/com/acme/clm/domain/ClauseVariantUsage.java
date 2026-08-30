package com.acme.clm.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "clause_variant_usage")
public class ClauseVariantUsage {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @Column(name = "clause_variant_id", nullable = false)
    public UUID clauseVariantId;
    @Column(name = "contract_id", nullable = false)
    public UUID contractId;
    @Column(name = "contract_version_id")
    public UUID contractVersionId;
    @Column(name = "was_modified")
    public boolean wasModified = false;
    @Column(name = "modification_diff", columnDefinition = "text")
    public String modificationDiff;
}
