package com.acme.clm.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "party")
public class Party {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @Column(name = "legal_name", nullable = false)
    public String legalName;
    @Column(name = "trading_name")
    public String tradingName;
    @Column(name = "country_code")
    public String countryCode;
    @Column(name = "registration_number")
    public String registrationNumber;
    @Column(name = "party_type")
    public String partyType = "VENDOR";
    public String industry;
    @Column(name = "size_band")
    public String sizeBand;
    @Column(name = "mdm_external_id")
    public String mdmExternalId;
    @Column(name = "mdm_system")
    public String mdmSystem;
    @Column(name = "mdm_last_synced_at")
    public Instant mdmLastSyncedAt;
    @Column(name = "merged_into_party_id")
    public UUID mergedIntoPartyId;
    @Column(name = "sanctions_check_status")
    public String sanctionsCheckStatus = "NOT_SCREENED";
    @Column(name = "sanctions_checked_at")
    public Instant sanctionsCheckedAt;
    @Column(name = "created_at")
    public Instant createdAt = Instant.now();
}
