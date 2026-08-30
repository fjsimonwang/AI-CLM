package com.acme.clm.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "workflow_definition")
public class WorkflowDefinition {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @Column(nullable = false)
    public String key;
    public String name;
    @Column(name = "version_no")
    public int versionNo = 1;
    public String status = "PUBLISHED";
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scope_expression")
    public String scopeExpression = "{}";
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    public String definition;
    @Column(name = "published_at")
    public Instant publishedAt = Instant.now();
}
