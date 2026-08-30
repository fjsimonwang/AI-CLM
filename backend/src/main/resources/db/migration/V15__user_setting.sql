-- Per-user preferences (dashboard layout, saved dashboard chart sections, ...).
CREATE TABLE user_setting (
    user_id   UUID NOT NULL,
    pref_key  VARCHAR(64) NOT NULL,
    value     JSONB,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, pref_key),
    FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);