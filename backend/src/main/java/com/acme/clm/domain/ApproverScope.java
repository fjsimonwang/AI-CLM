package com.acme.clm.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "approver_scope")
public class ApproverScope {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @Column(name = "user_id", nullable = false)
    public UUID userId;
    public String name;
    @JdbcTypeCode(SqlTypes.JSON)
    public String constraints = "{}";
    @Column(name = "is_active")
    public boolean isActive = true;
    @Column(name = "created_at")
    public Instant createdAt = Instant.now();
}
