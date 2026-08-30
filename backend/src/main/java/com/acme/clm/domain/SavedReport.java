package com.acme.clm.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "saved_report")
public class SavedReport {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @Column(name = "owner_user_id", nullable = false)
    public UUID ownerUserId;
    public String name;
    @Column(nullable = false, columnDefinition = "text")
    public String question;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "interpreted_query")
    public String interpretedQuery;
    @Column(name = "created_at")
    public Instant createdAt = Instant.now();
}
