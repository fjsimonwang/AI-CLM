-- Agent-to-agent collaboration (phase 1):
--  - per-contract switch allowing counterpart agents to discuss this contract
--  - agent-authored messages in the shared discussion threads (channel = AGENT)
ALTER TABLE contract ADD COLUMN agent_collab_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE comment_message ADD COLUMN channel VARCHAR(16) NOT NULL DEFAULT 'HUMAN';
ALTER TABLE comment_message ADD COLUMN author_agent VARCHAR(64);
-- agent messages have no human author
ALTER TABLE comment_message ALTER COLUMN author_user_id DROP NOT NULL;

CREATE INDEX idx_comment_message_channel ON comment_message (channel);