-- ============================================================
-- V3 — Workflow, assignment, obligations, audit
-- ============================================================

CREATE TABLE workflow_definition (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key              TEXT NOT NULL,
    name             TEXT NOT NULL,
    version_no       INT NOT NULL DEFAULT 1,
    status           TEXT NOT NULL DEFAULT 'PUBLISHED',
    scope_expression JSONB NOT NULL DEFAULT '{}'::jsonb,
    definition       JSONB NOT NULL,
    published_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (key, version_no)
);

CREATE TABLE workflow_instance (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id            UUID NOT NULL REFERENCES contract(id) ON DELETE CASCADE,
    workflow_definition_id UUID NOT NULL REFERENCES workflow_definition(id),
    workflow_key           TEXT NOT NULL,
    workflow_version_no    INT NOT NULL,
    current_state          TEXT NOT NULL,
    status                 TEXT NOT NULL DEFAULT 'RUNNING',
    started_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at           TIMESTAMPTZ,
    sla_due_at             TIMESTAMPTZ,
    is_escalated           BOOLEAN NOT NULL DEFAULT false
);
CREATE INDEX idx_wf_instance_contract ON workflow_instance(contract_id);

CREATE TABLE workflow_task (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_instance_id  UUID NOT NULL REFERENCES workflow_instance(id) ON DELETE CASCADE,
    state_key             TEXT NOT NULL,
    task_type             TEXT NOT NULL DEFAULT 'APPROVAL',
    assigned_role_expression TEXT,
    assigned_user_id      UUID REFERENCES app_user(id),
    assigned_team_id      UUID REFERENCES legal_team(id),
    status                TEXT NOT NULL DEFAULT 'OPEN',
    due_at                TIMESTAMPTZ,
    completed_at          TIMESTAMPTZ,
    outcome               TEXT,
    comments              TEXT,
    delegated_from_user_id UUID REFERENCES app_user(id),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_wf_task_instance ON workflow_task(workflow_instance_id);
CREATE INDEX idx_wf_task_assignee ON workflow_task(assigned_user_id);
CREATE INDEX idx_wf_task_status ON workflow_task(status);

CREATE TABLE assignment_rule (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                TEXT NOT NULL,
    priority            INT NOT NULL DEFAULT 100,
    condition_expression JSONB NOT NULL DEFAULT '{}'::jsonb,
    target_type         TEXT NOT NULL DEFAULT 'TEAM',
    target_id           UUID,
    fallback_rule_id    UUID,
    is_active           BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE obligation (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id           UUID NOT NULL REFERENCES contract(id) ON DELETE CASCADE,
    obligation_type       TEXT NOT NULL DEFAULT 'DELIVERABLE',
    description           TEXT NOT NULL,
    due_date              DATE,
    recurrence_rule       TEXT,
    owner_user_id         UUID REFERENCES app_user(id),
    owning_department     TEXT,
    status                TEXT NOT NULL DEFAULT 'OPEN',
    source_clause_variant_id UUID REFERENCES clause_variant(id),
    source_text_location  JSONB,
    extraction_confidence NUMERIC(4,3),
    verified_by           UUID REFERENCES app_user(id),
    alert_lead_days       INT NOT NULL DEFAULT 30,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_obligation_contract ON obligation(contract_id);
CREATE INDEX idx_obligation_due ON obligation(due_date);
CREATE INDEX idx_obligation_status ON obligation(status);

CREATE TABLE audit_event (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type    TEXT NOT NULL,
    entity_id      TEXT NOT NULL,
    action         TEXT NOT NULL,
    actor_user_id  UUID,
    actor_type     TEXT NOT NULL DEFAULT 'USER',
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    before_state   JSONB,
    after_state    JSONB,
    ai_model_id    TEXT,
    ai_prompt_hash TEXT,
    correlation_id TEXT
);
CREATE INDEX idx_audit_entity ON audit_event(entity_type, entity_id);
CREATE INDEX idx_audit_occurred ON audit_event(occurred_at);

-- append-only enforcement
CREATE OR REPLACE FUNCTION audit_event_block_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only (attempted %)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_no_update BEFORE UPDATE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION audit_event_block_mutation();
CREATE TRIGGER trg_audit_no_delete BEFORE DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION audit_event_block_mutation();
