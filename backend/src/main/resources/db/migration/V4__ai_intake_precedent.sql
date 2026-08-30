-- ============================================================
-- V4 — Intake sessions, AI interaction log, precedent, embeddings
-- ============================================================

CREATE TABLE intake_session (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_user_id   UUID NOT NULL REFERENCES app_user(id),
    channel             TEXT NOT NULL DEFAULT 'CHAT',
    status              TEXT NOT NULL DEFAULT 'OPEN',
    contract_type_code  TEXT,
    captured_fields     JSONB NOT NULL DEFAULT '{}'::jsonb,
    field_provenance    JSONB NOT NULL DEFAULT '{}'::jsonb,
    confidence_scores   JSONB NOT NULL DEFAULT '{}'::jsonb,
    conversation_history JSONB NOT NULL DEFAULT '[]'::jsonb,
    triage_result       JSONB,
    resulting_contract_id UUID REFERENCES contract(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_intake_requester ON intake_session(requester_user_id);

CREATE TABLE ai_interaction (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id       UUID REFERENCES contract(id) ON DELETE SET NULL,
    intake_session_id UUID REFERENCES intake_session(id) ON DELETE SET NULL,
    user_id           UUID REFERENCES app_user(id),
    surface           TEXT NOT NULL,
    capability        TEXT NOT NULL,
    model_id          TEXT,
    prompt_id         TEXT,
    prompt_version    TEXT,
    input_hash        TEXT,
    input_summary     JSONB,
    output            JSONB,
    confidence_score  NUMERIC(4,3),
    source_references JSONB,
    outcome           TEXT NOT NULL DEFAULT 'PENDING',
    edited_delta      JSONB,
    latency_ms        INT,
    token_cost        INT,
    occurred_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    correlation_id    TEXT,
    reverted_at       TIMESTAMPTZ,
    reverted_by       UUID REFERENCES app_user(id)
);
CREATE INDEX idx_ai_interaction_contract ON ai_interaction(contract_id);
CREATE INDEX idx_ai_interaction_surface ON ai_interaction(surface);
CREATE INDEX idx_ai_interaction_occurred ON ai_interaction(occurred_at);

CREATE TABLE precedent_link (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id          UUID NOT NULL REFERENCES contract(id) ON DELETE CASCADE,
    precedent_contract_id UUID NOT NULL REFERENCES contract(id),
    match_score          NUMERIC(5,4) NOT NULL,
    match_reasons        JSONB NOT NULL DEFAULT '[]'::jsonb,
    used_for             TEXT NOT NULL DEFAULT 'PREFILL',
    fields_inherited     JSONB,
    clauses_inherited    JSONB,
    deviations_carried   JSONB,
    acknowledged_by      UUID REFERENCES app_user(id),
    acknowledged_at      TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_precedent_link_contract ON precedent_link(contract_id);

CREATE TABLE contract_embedding (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id    UUID NOT NULL REFERENCES contract(id) ON DELETE CASCADE,
    embedding_type TEXT NOT NULL DEFAULT 'SUMMARY',
    embedding      vector(1536),
    source_text    TEXT,
    generated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    model_id       TEXT,
    UNIQUE (contract_id, embedding_type)
);

CREATE TABLE saved_report (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id UUID NOT NULL REFERENCES app_user(id),
    name          TEXT NOT NULL,
    question      TEXT NOT NULL,
    interpreted_query JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
