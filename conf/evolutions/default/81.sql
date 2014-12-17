# --- !Ups

BEGIN;

ALTER TABLE page DROP COLUMN reference_count;

COMMIT;

# --- !Downs

BEGIN;

-- We don't know the reference count any more. Keep pages forever.
ALTER TABLE page ADD COLUMN reference_count INT DEFAULT 9999;
ALTER TABLE page ALTER COLUMN reference_count SET NOT NULL;

COMMIT;
