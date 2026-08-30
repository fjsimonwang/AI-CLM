package com.acme.clm.domain;

import jakarta.persistence.*;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "assignment_rule")
public class AssignmentRule {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    public String name;
    public int priority = 100;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "condition_expression")
    public String conditionExpression = "{}";
    @Column(name = "target_type")
    public String targetType = "TEAM";
    @Column(name = "target_id")
    public UUID targetId;
    @Column(name = "fallback_rule_id")
    public UUID fallbackRuleId;
    @Column(name = "is_active")
    public boolean isActive = true;
}
