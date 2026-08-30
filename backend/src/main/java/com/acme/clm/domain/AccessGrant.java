package com.acme.clm.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "access_grant")
public class AccessGrant {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @Column(name = "user_id", nullable = false)
    public UUID userId;
    public String name;
    @JdbcTypeCode(SqlTypes.JSON)
    public String constraints = "{}";
    public String status = "ACTIVE";
    public String source = "REQUEST";
    @Column(name = "granted_by")
    public UUID grantedBy;
    @Column(name = "granted_at")
    public Instant grantedAt = Instant.now();
    @Column(name = "expires_at")
    public Instant expiresAt;
    @Column(name = "request_id")
    public UUID requestId;
}
