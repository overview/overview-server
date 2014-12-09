# --- !Ups

BEGIN;

ALTER TABLE page ADD COLUMN data_size BIGINT;

COMMIT;

# --- !Downs

BEGIN;

ALTER TABLE page DROP COLUMN data_size;

COMMIT;
