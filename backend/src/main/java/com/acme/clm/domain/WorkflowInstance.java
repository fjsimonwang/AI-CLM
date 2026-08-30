package com.acme.clm.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflow_instance")
public class WorkflowInstance {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @Column(name = "contract_id", nullable = false)
    public UUID contractId;
    @Column(name = "workflow_definition_id", nullable = false)
    public UUID workflowDefinitionId;
    @Column(name = "workflow_key", nullable = false)
    public String workflowKey;
    @Column(name = "workflow_version_no", nullable = false)
    public int workflowVersionNo;
    @Column(name = "current_state", nullable = false)
    public String currentState;
    public String status = "RUNNING";
    @Column(name = "started_at")
    public Instant startedAt = Instant.now();
    @Column(name = "completed_at")
    public Instant completedAt;
    @Column(name = "sla_due_at")
    public Instant slaDueAt;
    @Column(name = "is_escalated")
    public boolean isEscalated = false;
}
