-- Rejection now returns a request to the requestor as a DRAFT (with the reason) rather than
-- closing it outright. The requestor then revises & resubmits, or closes it explicitly.
ALTER TABLE contract ADD COLUMN rejection_reason text;
