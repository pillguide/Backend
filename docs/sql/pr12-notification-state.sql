-- Apply before starting the revised PR #12 backend, in one transaction.
-- Existing attempt timestamps are NOT proof of successful FCM acceptance.
ALTER TABLE medication_dose
    ADD COLUMN IF NOT EXISTS notification_accepted_at timestamp(6),
    ADD COLUMN IF NOT EXISTS notification_retry_after timestamp(6),
    ADD COLUMN IF NOT EXISTS notification_stopped_at timestamp(6);
