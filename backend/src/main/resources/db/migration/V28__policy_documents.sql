-- CLM policy & procedure documents the help chat learns from, scoped by access dimensions.
-- Empty roles/countries/regions means "all"; confidential restricts to broad-access users.
CREATE TABLE policy_document (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    title        text NOT NULL,
    body_html    text NOT NULL DEFAULT '',
    roles        text NOT NULL DEFAULT '',
    countries    text NOT NULL DEFAULT '',
    regions      text NOT NULL DEFAULT '',
    confidential boolean NOT NULL DEFAULT false,
    is_active    boolean NOT NULL DEFAULT true,
    uploaded_by  uuid,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now()
);
