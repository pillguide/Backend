-- P1 authentication schema transition. Apply only after the DEV preflight.
-- Existing members and refresh tokens are retained. Legacy JWTs require login.
BEGIN;

-- A duplicate member token must be reviewed, never silently discarded.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM refresh_token GROUP BY member_id HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Refresh token member duplicates require review before migration';
    END IF;
END $$;

ALTER TABLE member ADD COLUMN IF NOT EXISTS auth_version bigint DEFAULT 0;
UPDATE member SET auth_version = 0 WHERE auth_version IS NULL;
ALTER TABLE member ALTER COLUMN auth_version SET DEFAULT 0;
ALTER TABLE member ALTER COLUMN auth_version SET NOT NULL;

-- Purpose, random jti and authentication version exceed the old 200-char limit.
ALTER TABLE refresh_token ALTER COLUMN token TYPE varchar(512);
CREATE UNIQUE INDEX IF NOT EXISTS uk_refresh_token_member ON refresh_token(member_id);

COMMIT;
