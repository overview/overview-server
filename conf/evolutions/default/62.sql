# --- !Ups

BEGIN;

UPDATE tree SET description = '' WHERE description IS NULL;

ALTER TABLE tree ALTER COLUMN description SET NOT NULL;

COMMIT;

# --- !Downs

BEGIN;

ALTER TABLE tree ALTER COLUMN description DROP NOT NULL;

COMMIT;
