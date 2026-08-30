package com.acme.clm.domain;

import jakarta.persistence.*;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "contract_type_definition")
public class ContractTypeDefinition {
    @Id
    public String code;
    @Column(name = "display_name", nullable = false)
    public String displayName;
    public String category;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_schema")
    public String fieldSchema = "{}";
    @Column(name = "default_template_id")
    public UUID defaultTemplateId;
    @Column(name = "default_workflow_id")
    public UUID defaultWorkflowId;
    @Column(name = "requires_legal_review_default")
    public boolean requiresLegalReviewDefault = true;
    @Column(name = "retention_years")
    public int retentionYears = 7;
    @Column(name = "base_risk")
    public int baseRisk = 20;
    @Column(name = "auto_issue_allowed")
    public boolean autoIssueAllowed = false;
    @Column(name = "is_active")
    public boolean isActive = true;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ui_groups")
    public String uiGroups = "[]";
    public String icon;
}
