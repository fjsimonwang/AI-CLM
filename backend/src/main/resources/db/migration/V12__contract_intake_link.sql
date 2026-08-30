ALTER TABLE contract ADD COLUMN intake_session_id UUID;

UPDATE contract c
SET intake_session_id = s.id
FROM intake_session s
WHERE s.resulting_contract_id = c.id
  AND c.intake_session_id IS NULL;