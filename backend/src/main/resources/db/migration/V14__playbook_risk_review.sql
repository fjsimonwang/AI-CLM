-- Admin-uploaded negotiation playbooks, scoped by contract type / entity / jurisdiction
CREATE TABLE playbook (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name               TEXT NOT NULL,
    contract_type_code TEXT REFERENCES contract_type_definition(code),
    legal_entity_id    UUID REFERENCES legal_entity(id),
    jurisdiction       TEXT,
    language           TEXT NOT NULL DEFAULT 'EN',
    description        TEXT,
    body_html          TEXT NOT NULL DEFAULT '',
    is_active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_by         UUID REFERENCES app_user(id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Background AI document review runs (survive page navigation)
CREATE TABLE ai_review_run (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id  UUID NOT NULL REFERENCES contract(id) ON DELETE CASCADE,
    requested_by UUID REFERENCES app_user(id),
    status       TEXT NOT NULL DEFAULT 'RUNNING',
    overall      TEXT,
    findings     JSONB,
    model_id     TEXT,
    error        TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);
CREATE INDEX idx_review_run_contract ON ai_review_run(contract_id);

-- Per-contract risk register; AI review findings land here alongside manual entries
CREATE TABLE contract_risk (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id UUID NOT NULL REFERENCES contract(id) ON DELETE CASCADE,
    title       TEXT NOT NULL,
    category    TEXT,
    severity    TEXT NOT NULL DEFAULT 'MEDIUM',
    detail      TEXT,
    location    TEXT,
    source      TEXT NOT NULL DEFAULT 'MANUAL',
    dedupe_key  TEXT,
    status      TEXT NOT NULL DEFAULT 'OPEN',
    created_by  UUID REFERENCES app_user(id),
    closed_by   UUID REFERENCES app_user(id),
    closed_at   TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_contract_risk_contract ON contract_risk(contract_id);