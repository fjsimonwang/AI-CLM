-- Global per-approver trigger timing for auto-rejection: evaluate rules as soon as a task
-- lands in the approver's queue (IMMEDIATE), or only after a delay since assignment (DELAYED).
CREATE TABLE auto_reject_setting (
    owner_user_id UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    mode VARCHAR(16) NOT NULL DEFAULT 'IMMEDIATE',
    delay_hours INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);