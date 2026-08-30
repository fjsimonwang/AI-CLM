package com.acme.clm.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_setting")
@IdClass(UserSetting.Key.class)
public class UserSetting {

    @Id @Column(name = "user_id")
    public UUID userId;
    @Id @Column(name = "pref_key", length = 64)
    public String prefKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public String value;

    @Column(name = "updated_at")
    public Instant updatedAt = Instant.now();

    public static class Key implements Serializable {
        public UUID userId;
        public String prefKey;
        public Key() {}
        public Key(UUID userId, String prefKey) { this.userId = userId; this.prefKey = prefKey; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Key k)) return false;
            return Objects.equals(userId, k.userId) && Objects.equals(prefKey, k.prefKey);
        }
        @Override public int hashCode() { return Objects.hash(userId, prefKey); }
    }
}