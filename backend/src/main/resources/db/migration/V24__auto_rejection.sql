-- Approver auto-rejection rules: an approver describes rejection conditions in natural
-- language, AI structures them into executable requirements, and when a contract reaches
-- the rule owner's approval stage without meeting them it is rejected automatically.
CREATE TABLE auto_reject_rule (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL REFERENCES app_user (id),
    name VARCHAR(200) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    scope JSONB NOT NULL DEFAULT '{}'::jsonb,      -- {"contractTypes":[],"countries":[],"entityIds":[]}
    instructions TEXT NOT NULL,                     -- approver's own words
    structured JSONB,                               -- AI-interpreted executable form
    interpretation_model VARCHAR(100),
    interpreted_at TIMESTAMPTZ,
    fired_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_auto_reject_rule_owner ON auto_reject_rule (owner_user_id);