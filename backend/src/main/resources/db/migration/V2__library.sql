-- ============================================================
-- V2 — Clause library & template composition
-- ============================================================

CREATE TABLE clause_concept (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    concept_code         TEXT NOT NULL UNIQUE,
    name                 TEXT NOT NULL,
    category             TEXT NOT NULL,
    description          TEXT,
    risk_category        TEXT,
    is_core              BOOLEAN NOT NULL DEFAULT false,
    owning_legal_team_id UUID REFERENCES legal_team(id)
);

CREATE TABLE clause_variant (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clause_concept_id         UUID NOT NULL REFERENCES clause_concept(id),
    version_no                INT NOT NULL DEFAULT 1,
    jurisdiction_code         TEXT NOT NULL DEFAULT 'GLOBAL',
    language_code             TEXT NOT NULL DEFAULT 'en',
    body_text                 TEXT NOT NULL,
    position_tier             TEXT NOT NULL DEFAULT 'ACCEPTABLE',
    risk_tier                 TEXT NOT NULL DEFAULT 'MEDIUM',
    guidance_notes            TEXT,
    canonical_source_variant_id UUID REFERENCES clause_variant(id),
    translation_status        TEXT NOT NULL DEFAULT 'CURRENT',
    approved_by               UUID REFERENCES app_user(id),
    approved_at               TIMESTAMPTZ,
    status                    TEXT NOT NULL DEFAULT 'ACTIVE',
    embedding                 vector(1536)
);
CREATE INDEX idx_variant_concept ON clause_variant(clause_concept_id);
CREATE INDEX idx_variant_status ON clause_variant(status);

CREATE TABLE clause_variant_usage (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clause_variant_id   UUID NOT NULL REFERENCES clause_variant(id),
    contract_id         UUID NOT NULL REFERENCES contract(id) ON DELETE CASCADE,
    contract_version_id UUID REFERENCES contract_version(id),
    was_modified        BOOLEAN NOT NULL DEFAULT false,
    modification_diff   TEXT
);
CREATE INDEX idx_variant_usage_variant ON clause_variant_usage(clause_variant_id);
CREATE INDEX idx_variant_usage_contract ON clause_variant_usage(contract_id);

CREATE TABLE template (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                 TEXT NOT NULL,
    contract_type_code   TEXT NOT NULL REFERENCES contract_type_definition(code),
    legal_entity_id      UUID REFERENCES legal_entity(id),
    jurisdiction_code    TEXT NOT NULL DEFAULT 'GLOBAL',
    language_code        TEXT NOT NULL DEFAULT 'en',
    version_no           INT NOT NULL DEFAULT 1,
    status               TEXT NOT NULL DEFAULT 'ACTIVE',
    owning_legal_team_id UUID REFERENCES legal_team(id),
    approved_by          UUID REFERENCES app_user(id),
    approved_at          TIMESTAMPTZ
);

CREATE TABLE template_section (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id              UUID NOT NULL REFERENCES template(id) ON DELETE CASCADE,
    sort_order               INT NOT NULL,
    heading                  TEXT NOT NULL,
    is_optional              BOOLEAN NOT NULL DEFAULT false,
    inclusion_condition      TEXT,
    clause_concept_id        UUID REFERENCES clause_concept(id),
    default_clause_variant_id UUID REFERENCES clause_variant(id),
    static_body              TEXT
);
CREATE INDEX idx_template_section_template ON template_section(template_id);

CREATE TABLE merge_field (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id UUID NOT NULL REFERENCES template(id) ON DELETE CASCADE,
    field_key   TEXT NOT NULL,
    source_path TEXT NOT NULL,
    format_mask TEXT,
    is_required BOOLEAN NOT NULL DEFAULT false
);
