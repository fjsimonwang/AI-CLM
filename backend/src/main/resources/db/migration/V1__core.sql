-- ============================================================
-- V1 — Extensions, organization & identity, contract core
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "vector";

-- ---------- Organization & identity ----------

CREATE TABLE legal_team (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                  TEXT NOT NULL,
    region                TEXT,
    default_queue_sla_hours INT NOT NULL DEFAULT 48
);

CREATE TABLE legal_entity (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legal_name            TEXT NOT NULL,
    short_name            TEXT NOT NULL UNIQUE,
    country_code          TEXT NOT NULL,
    registration_number   TEXT,
    default_governing_law TEXT,
    default_language      TEXT NOT NULL DEFAULT 'en',
    parent_entity_id      UUID REFERENCES legal_entity(id),
    data_residency_region TEXT NOT NULL DEFAULT 'GLOBAL',
    legal_team_id         UUID REFERENCES legal_team(id),
    status                TEXT NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE app_user (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_idp_id  TEXT,
    email            TEXT NOT NULL UNIQUE,
    password_hash    TEXT NOT NULL,
    display_name     TEXT NOT NULL,
    default_entity_id UUID REFERENCES legal_entity(id),
    department       TEXT,
    cost_center      TEXT,
    manager_user_id  UUID REFERENCES app_user(id),
    hr_system_id     TEXT,
    roles            TEXT NOT NULL DEFAULT 'REQUESTER',
    status           TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE signing_authority (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legal_entity_id     UUID NOT NULL REFERENCES legal_entity(id),
    user_id             UUID NOT NULL REFERENCES app_user(id),
    contract_type_code  TEXT,
    max_value_amount    NUMERIC(18,2),
    currency            TEXT NOT NULL DEFAULT 'EUR',
    valid_from          DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_to            DATE,
    delegated_from_user_id UUID REFERENCES app_user(id)
);

CREATE TABLE user_delegation (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    from_user_id UUID NOT NULL REFERENCES app_user(id),
    to_user_id   UUID NOT NULL REFERENCES app_user(id),
    scope        TEXT NOT NULL DEFAULT 'ALL',
    valid_from   DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_to     DATE
);

-- ---------- Contract type configuration ----------

CREATE TABLE contract_type_definition (
    code                          TEXT PRIMARY KEY,
    display_name                  TEXT NOT NULL,
    category                      TEXT NOT NULL,
    field_schema                  JSONB NOT NULL DEFAULT '{}'::jsonb,
    default_template_id           UUID,
    default_workflow_id           UUID,
    requires_legal_review_default BOOLEAN NOT NULL DEFAULT true,
    retention_years               INT NOT NULL DEFAULT 7,
    base_risk                     INT NOT NULL DEFAULT 20,
    auto_issue_allowed            BOOLEAN NOT NULL DEFAULT false,
    is_active                     BOOLEAN NOT NULL DEFAULT true
);

-- ---------- Parties ----------

CREATE TABLE party (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legal_name             TEXT NOT NULL,
    trading_name           TEXT,
    country_code           TEXT,
    registration_number    TEXT,
    party_type             TEXT NOT NULL DEFAULT 'VENDOR',
    industry               TEXT,
    size_band              TEXT,
    mdm_external_id        TEXT,
    mdm_system             TEXT,
    mdm_last_synced_at     TIMESTAMPTZ,
    merged_into_party_id   UUID REFERENCES party(id),
    sanctions_check_status TEXT NOT NULL DEFAULT 'NOT_SCREENED',
    sanctions_checked_at   TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------- Contract core ----------

CREATE TABLE contract (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_number        TEXT NOT NULL UNIQUE,
    contract_type_code     TEXT NOT NULL REFERENCES contract_type_definition(code),
    title                  TEXT NOT NULL,
    status                 TEXT NOT NULL DEFAULT 'DRAFT',
    contracting_entity_id  UUID NOT NULL REFERENCES legal_entity(id),
    parent_contract_id     UUID REFERENCES contract(id),
    relationship_type      TEXT,
    template_id            UUID,
    governing_law_code     TEXT,
    dispute_resolution_forum TEXT,
    primary_language       TEXT NOT NULL DEFAULT 'en',
    prevailing_language    TEXT,
    effective_date         DATE,
    expiry_date            DATE,
    notice_period_days     INT,
    auto_renew             BOOLEAN NOT NULL DEFAULT false,
    renewal_term_months    INT,
    value_amount           NUMERIC(18,2),
    currency               TEXT,
    value_basis            TEXT,
    risk_score             INT,
    risk_tier              TEXT,
    source                 TEXT NOT NULL DEFAULT 'NATIVE',
    confidentiality_level  TEXT NOT NULL DEFAULT 'INTERNAL',
    owner_user_id          UUID REFERENCES app_user(id),
    assigned_lawyer_id     UUID REFERENCES app_user(id),
    type_attributes        JSONB NOT NULL DEFAULT '{}'::jsonb,
    summary                TEXT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by             UUID REFERENCES app_user(id),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by             UUID REFERENCES app_user(id)
);

CREATE INDEX idx_contract_entity   ON contract(contracting_entity_id);
CREATE INDEX idx_contract_type     ON contract(contract_type_code);
CREATE INDEX idx_contract_status   ON contract(status);
CREATE INDEX idx_contract_expiry   ON contract(expiry_date);
CREATE INDEX idx_contract_parent   ON contract(parent_contract_id);
CREATE INDEX idx_contract_attrs    ON contract USING GIN (type_attributes);

CREATE TABLE contract_party (
    contract_id      UUID NOT NULL REFERENCES contract(id) ON DELETE CASCADE,
    party_id         UUID NOT NULL REFERENCES party(id),
    role             TEXT NOT NULL DEFAULT 'COUNTERPARTY',
    signatory_name   TEXT,
    signatory_email  TEXT,
    signatory_title  TEXT,
    notice_address   JSONB,
    PRIMARY KEY (contract_id, party_id, role)
);

CREATE TABLE contract_term (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id          UUID NOT NULL REFERENCES contract(id) ON DELETE CASCADE,
    term_key             TEXT NOT NULL,
    term_value           JSONB NOT NULL,
    term_type            TEXT NOT NULL DEFAULT 'STRING',
    is_inherited         BOOLEAN NOT NULL DEFAULT false,
    source_contract_id   UUID REFERENCES contract(id),
    effective_from       DATE,
    effective_to         DATE,
    extraction_confidence NUMERIC(4,3),
    verified_by          UUID REFERENCES app_user(id),
    verified_at          TIMESTAMPTZ
);
CREATE INDEX idx_term_contract ON contract_term(contract_id);
CREATE INDEX idx_term_key ON contract_term(term_key);

CREATE TABLE contract_version (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id    UUID NOT NULL REFERENCES contract(id) ON DELETE CASCADE,
    version_no     INT NOT NULL,
    version_label  TEXT,
    change_summary TEXT,
    body_text      TEXT,
    is_executed    BOOLEAN NOT NULL DEFAULT false,
    created_by     UUID REFERENCES app_user(id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (contract_id, version_no)
);

CREATE TABLE document_file (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id       UUID REFERENCES contract(id) ON DELETE CASCADE,
    contract_version_id UUID REFERENCES contract_version(id),
    file_name         TEXT NOT NULL,
    content_type      TEXT,
    byte_size         BIGINT,
    storage_key       TEXT,
    sha256            TEXT,
    kind              TEXT NOT NULL DEFAULT 'DRAFT',
    esign_envelope_id TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
