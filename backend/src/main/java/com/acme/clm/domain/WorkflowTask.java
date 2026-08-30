package com.acme.clm.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflow_task")
public class WorkflowTask {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @Column(name = "workflow_instance_id", nullable = false)
    public UUID workflowInstanceId;
    @Column(name = "state_key", nullable = false)
    public String stateKey;
    @Column(name = "task_type")
    public String taskType = "APPROVAL";
    @Column(name = "assigned_role_expression")
    public String assignedRoleExpression;
    @Column(name = "assigned_user_id")
    public UUID assignedUserId;
    @Column(name = "assigned_team_id")
    public UUID assignedTeamId;
    public String status = "OPEN";
    @Column(name = "due_at")
    public Instant dueAt;
    @Column(name = "completed_at")
    public Instant completedAt;
    public String outcome;
    @Column(columnDefinition = "text")
    public String comments;
    @Column(name = "delegated_from_user_id")
    public UUID delegatedFromUserId;
    @Column(name = "created_at")
    public Instant createdAt = Instant.now();
}
