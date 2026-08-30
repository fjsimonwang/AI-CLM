package com.acme.clm.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "signing_authority")
public class SigningAuthority {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @Column(name = "legal_entity_id", nullable = false)
    public UUID legalEntityId;
    @Column(name = "user_id", nullable = false)
    public UUID userId;
    @Column(name = "contract_type_code")
    public String contractTypeCode;
    @Column(name = "max_value_amount")
    public BigDecimal maxValueAmount;
    public String currency = "EUR";
    @Column(name = "valid_from")
    public LocalDate validFrom = LocalDate.now();
    @Column(name = "valid_to")
    public LocalDate validTo;
    @Column(name = "delegated_from_user_id")
    public UUID delegatedFromUserId;
}
