package com.acme.clm.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "contract_party")
@IdClass(ContractParty.Key.class)
public class ContractParty {

    @Id @Column(name = "contract_id")
    public UUID contractId;
    @Id @Column(name = "party_id")
    public UUID partyId;
    @Id
    public String role = "COUNTERPARTY";

    @Column(name = "signatory_name")
    public String signatoryName;
    @Column(name = "signatory_email")
    public String signatoryEmail;
    @Column(name = "signatory_title")
    public String signatoryTitle;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notice_address")
    public String noticeAddress;

    public static class Key implements Serializable {
        public UUID contractId;
        public UUID partyId;
        public String role;
        public Key() {}
        public Key(UUID contractId, UUID partyId, String role) {
            this.contractId = contractId; this.partyId = partyId; this.role = role;
        }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Key k)) return false;
            return Objects.equals(contractId, k.contractId) && Objects.equals(partyId, k.partyId) && Objects.equals(role, k.role);
        }
        @Override public int hashCode() { return Objects.hash(contractId, partyId, role); }
    }
}
