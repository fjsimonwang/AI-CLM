-- Contract relations: explicit (parent/child, precedents) plus AI-detected relations awaiting user confirmation.
CREATE TABLE contract_relation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id UUID NOT NULL,
    related_contract_id UUID NOT NULL,
    relation_type VARCHAR(40) NOT NULL,
    source VARCHAR(20) NOT NULL DEFAULT 'AI',
    status VARCHAR(20) NOT NULL DEFAULT 'SUGGESTED',
    confidence DOUBLE PRECISION,
    reasons TEXT,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_by UUID,
    decided_at TIMESTAMPTZ,
    CONSTRAINT uq_contract_relation UNIQUE (contract_id, related_contract_id, relation_type)
);
CREATE INDEX ix_contract_relation_contract ON contract_relation(contract_id);
CREATE INDEX ix_contract_relation_related ON contract_relation(related_contract_id);