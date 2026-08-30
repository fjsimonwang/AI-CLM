-- §AI document review: separately-defined rule checklist the AI must check every draft against.
CREATE TABLE ai_review_rule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(80) NOT NULL UNIQUE,
    label VARCHAR(200) NOT NULL,
    instruction TEXT NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'HIGH',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort INT NOT NULL DEFAULT 0,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO ai_review_rule (code, label, instruction, severity, sort) VALUES
 ('RECORD_CONSISTENCY', 'Document matches contract record',
  'Verify that party legal names, the contracting entity, term length, dates and any value/fee amounts mentioned in the document match the structured contract record. Flag every mismatch, however small.', 'CRITICAL', 10),
 ('MISSING_SECTIONS', 'Mandatory sections present',
  'Flag any missing mandatory sections: definitions, scope, term, payment, confidentiality, limitation of liability, termination, governing law, signature block.', 'CRITICAL', 20),
 ('TERMINATION_CLARITY', 'Termination mechanics clear',
  'Ensure termination rights (for convenience and for cause) are defined with explicit notice periods; flag vague or one-sided termination language.', 'HIGH', 30),
 ('PAYMENT_TERMS', 'Payment terms complete',
  'Verify payment amounts, invoicing cadence, due days and late-payment provisions are stated and internally consistent.', 'HIGH', 40),
 ('LIABILITY_CAP', 'Liability is capped',
  'Confirm a limitation-of-liability clause exists, states a cap, and is not unlimited; note the amount and carve-outs.', 'MEDIUM', 50),
 ('GOVERNING_LAW', 'Governing law matches record',
  'Confirm the governing law and dispute-resolution language matches the record; flag absence of either.', 'MEDIUM', 60),
 ('DATA_PROTECTION', 'Personal data handling present',
  'If the contract type or record indicates processing of personal data, ensure DPA / data-processing clauses are present; flag if they are referenced but not included.', 'HIGH', 70),
 ('AUTO_RENEWAL', 'Renewal mechanics match record',
  'Verify renewal type, renewal term and notice period in the document match the record and are unambiguous.', 'MEDIUM', 80);