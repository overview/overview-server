# --- !Ups

BEGIN;

ALTER TABLE page ADD COLUMN data_location VARCHAR;

COMMIT;

# --- !Downs

BEGIN;

ALTER TABLE page DROP COLUMN data_location;

COMMIT;


