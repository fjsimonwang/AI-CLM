-- Saved intake drafts get a references request number; submitted drafts leave the list.
ALTER TABLE intake_session ADD COLUMN request_number TEXT;
CREATE UNIQUE INDEX idx_intake_request_number ON intake_session(request_number) WHERE request_number IS NOT NULL;

UPDATE intake_session SET request_number = 'REQ-' || to_char(created_at, 'YYYY') || '-' || lpad(n::text, 4, '0')
FROM (
    SELECT id, row_number() OVER (ORDER BY created_at) AS n
    FROM intake_session WHERE saved = true
) sub
WHERE intake_session.id = sub.id;