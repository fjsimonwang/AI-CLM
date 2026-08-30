-- Drop the 'request changes' approver action from the legal-review workflow:
-- the requestor drives corrections via recall-to-draft + resubmit instead.
UPDATE workflow_definition
SET definition = jsonb_set(definition, '{states,0,transitions}', '[{"on":"approve","to":"finance_review"},{"on":"reject","to":"closed_rejected"}]'::jsonb)
WHERE key = 'legal-review' AND version_no = 1;