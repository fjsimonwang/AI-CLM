package com.acme.clm.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "contract_participant")
@IdClass(ContractParticipant.Key.class)
public class ContractParticipant {

    @Id @Column(name = "contract_id")
    public UUID contractId;
    @Id @Column(name = "user_id")
    public UUID userId;

    public String role = "VIEWER";
    @Column(name = "added_by")
    public UUID addedBy;
    @Column(name = "added_at")
    public Instant addedAt = Instant.now();

    public static class Key implements Serializable {
        public UUID contractId;
        public UUID userId;
        public Key() {}
        public Key(UUID contractId, UUID userId) { this.contractId = contractId; this.userId = userId; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Key k)) return false;
            return Objects.equals(contractId, k.contractId) && Objects.equals(userId, k.userId);
        }
        @Override public int hashCode() { return Objects.hash(contractId, userId); }
    }
}
