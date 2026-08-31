-- Agent phase 1 rework: agents represent participants. Records which user an agent message speaks for.
ALTER TABLE comment_message ADD COLUMN represented_user_id UUID;
CREATE INDEX idx_comment_message_represented ON comment_message (represented_user_id);