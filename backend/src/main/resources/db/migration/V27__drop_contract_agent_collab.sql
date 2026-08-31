-- Agent talk is now governed solely by each user's personal opt-in (the header toggle);
-- the per-contract switch is gone.
ALTER TABLE contract DROP COLUMN IF EXISTS agent_collab_enabled;
