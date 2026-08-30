-- ============================================================
-- V8 — Contract participants + attribute-based access (security groups)
-- ============================================================

-- ---------- Per-contract participants (explicit access) ----------
CREATE TABLE contract_participant (
    contract_id  UUID NOT NULL REFERENCES contract(id) ON DELETE CASCADE,
    user_id      UUID NOT NULL REFERENCES app_user(id),
    role         TEXT NOT NULL DEFAULT 'VIEWER',   -- VIEWER | CONTRIBUTOR | APPROVER | SIGNATORY
    added_by     UUID REFERENCES app_user(id),
    added_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (contract_id, user_id)
);
CREATE INDEX idx_contract_participant_user ON contract_participant(user_id);

-- ---------- Access dimensions (admin-defined) ----------
-- derivation tells how to compute a contract's value on this dimension:
--   field:<column>            -> a column on contract (e.g. field:contract_type_code)
--   entity:<column>           -> a column on the contracting legal_entity (e.g. entity:data_residency_region)
--   type_attribute:<key>      -> a key inside contract.type_attributes
--   owner_department          -> the owner's department
CREATE TABLE access_dimension (
    code          TEXT PRIMARY KEY,
    name          TEXT NOT NULL,
    description   TEXT,
    derivation    TEXT NOT NULL,
    value_options JSONB NOT NULL DEFAULT '[]'::jsonb,   -- suggested values for the UI (free-form allowed)
    sort_order    INT NOT NULL DEFAULT 100,
    is_active     BOOLEAN NOT NULL DEFAULT true
);

-- ---------- A user's granted access scope ----------
-- constraints: { "<dimension_code>": ["value", ...] }  — "*" (or dimension absent) means "any".
CREATE TABLE access_grant (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES app_user(id),
    name        TEXT,
    constraints JSONB NOT NULL DEFAULT '{}'::jsonb,
    status      TEXT NOT NULL DEFAULT 'ACTIVE',       -- ACTIVE | REVOKED | EXPIRED
    source      TEXT NOT NULL DEFAULT 'REQUEST',      -- REQUEST | DIRECT | ROLE
    granted_by  UUID REFERENCES app_user(id),
    granted_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ,
    request_id  UUID
);
CREATE INDEX idx_access_grant_user ON access_grant(user_id, status);

-- ---------- Access requests (applications) ----------
CREATE TABLE access_request (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES app_user(id),
    constraints   JSONB NOT NULL DEFAULT '{}'::jsonb,
    justification TEXT,
    status        TEXT NOT NULL DEFAULT 'PENDING',    -- PENDING | APPROVED | REJECTED | WITHDRAWN
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_by    UUID REFERENCES app_user(id),
    decided_at    TIMESTAMPTZ,
    decision_note TEXT,
    expires_days  INT
);
CREATE INDEX idx_access_request_status ON access_request(status);

-- ---------- Approver scopes (who can approve which access) ----------
-- constraints: same shape. An approver may approve a request only if, for EVERY
-- dimension the request constrains, this scope covers the requested values
-- ("*" / dimension absent = covers anything).
CREATE TABLE approver_scope (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES app_user(id),
    name        TEXT,
    constraints JSONB NOT NULL DEFAULT '{}'::jsonb,
    is_active   BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_approver_scope_user ON approver_scope(user_id);

-- ---------- Seed dimensions ----------
INSERT INTO access_dimension (code, name, description, derivation, value_options, sort_order) VALUES
 ('REGION', 'Region', 'Data-residency region of the contracting entity.', 'entity:data_residency_region',
   '["EU","US","UK","GLOBAL"]'::jsonb, 10),
 ('CONTRACT_TYPE', 'Contract type', 'The type of the contract.', 'field:contract_type_code',
   '["NDA","MSA","DPA","SOW","VENDOR_PURCHASE","EMPLOYMENT"]'::jsonb, 20),
 ('ENTITY', 'Legal entity', 'The contracting legal entity.', 'entity:short_name',
   '["ACME_GMBH","ACME_SAS","ACME_INC","ACME_LTD"]'::jsonb, 30),
 ('FUNCTION', 'Business function', 'Requesting department / function.', 'owner_department',
   '["Legal","Finance","Procurement","Sales","HR"]'::jsonb, 40),
 ('CONFIDENTIALITY', 'Confidentiality', 'Confidentiality tier of the contract.', 'field:confidentiality_level',
   '["PUBLIC","INTERNAL","CONFIDENTIAL","RESTRICTED"]'::jsonb, 50);

-- ---------- Seed: existing participants for the sample contracts ----------
INSERT INTO contract_participant (contract_id, user_id, role, added_by) VALUES
 ('99999999-9999-9999-9999-999999999906'::uuid, '33333333-3333-3333-3333-333333333306'::uuid, 'CONTRIBUTOR', '33333333-3333-3333-3333-333333333306'::uuid),
 ('99999999-9999-9999-9999-999999999906'::uuid, '33333333-3333-3333-3333-333333333302'::uuid, 'APPROVER',    '33333333-3333-3333-3333-333333333306'::uuid),
 ('99999999-9999-9999-9999-999999999902'::uuid, '33333333-3333-3333-3333-333333333305'::uuid, 'CONTRIBUTOR', '33333333-3333-3333-3333-333333333305'::uuid),
 ('99999999-9999-9999-9999-999999999902'::uuid, '33333333-3333-3333-3333-333333333304'::uuid, 'VIEWER',      '33333333-3333-3333-3333-333333333305'::uuid);

-- ---------- Seed: grants + approver scopes matching the plan example ----------
-- Priya (EMEA counsel) can see EMEA contracts of any type.
INSERT INTO access_grant (user_id, name, constraints, source, granted_by) VALUES
 ('33333333-3333-3333-3333-333333333302'::uuid, 'EMEA — all types', '{"REGION":["EU","UK"]}'::jsonb, 'DIRECT', '33333333-3333-3333-3333-333333333301'::uuid),
 ('33333333-3333-3333-3333-333333333303'::uuid, 'Americas — all types', '{"REGION":["US"]}'::jsonb, 'DIRECT', '33333333-3333-3333-3333-333333333301'::uuid),
 ('33333333-3333-3333-3333-333333333304'::uuid, 'Finance — commercial', '{"CONTRACT_TYPE":["MSA","SOW","VENDOR_PURCHASE"]}'::jsonb, 'DIRECT', '33333333-3333-3333-3333-333333333301'::uuid);

-- Approver A: owns EMEA + Sales function only.  Approver B: owns Sales function, any region.
INSERT INTO approver_scope (user_id, name, constraints) VALUES
 ('33333333-3333-3333-3333-333333333302'::uuid, 'EMEA / Sales access owner', '{"REGION":["EU","UK"],"FUNCTION":["Sales"]}'::jsonb),
 ('33333333-3333-3333-3333-333333333301'::uuid, 'Sales access owner (all regions)', '{"FUNCTION":["Sales"]}'::jsonb),
 ('33333333-3333-3333-3333-333333333301'::uuid, 'Global access owner', '{}'::jsonb);
