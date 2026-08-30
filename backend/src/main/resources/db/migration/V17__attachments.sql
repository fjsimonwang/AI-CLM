CREATE TABLE intake_attachment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    intake_session_id UUID NOT NULL REFERENCES intake_session(id),
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(255),
    size_bytes BIGINT NOT NULL DEFAULT 0,
    content BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_intake_attachment_session ON intake_attachment(intake_session_id);

CREATE TABLE contract_attachment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id UUID NOT NULL REFERENCES contract(id),
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(255),
    size_bytes BIGINT NOT NULL DEFAULT 0,
    content BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_contract_attachment_contract ON contract_attachment(contract_id);