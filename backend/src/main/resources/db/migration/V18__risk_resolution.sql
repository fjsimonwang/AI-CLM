-- How a risk was closed: RESOLVED | DISMISSED (null while open)
ALTER TABLE contract_risk ADD COLUMN resolution TEXT;