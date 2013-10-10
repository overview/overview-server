# --- !Ups

BEGIN;

ALTER TABLE grouped_file_upload DROP COLUMN last_modified_date;

COMMIT;


# --- !Downs

BEGIN;

ALTER TABLE grouped_file_upload ADD COLUMN last_modified_date VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE grouped_file_upload ALTER COLUMN last_modified_date DROP DEFAULT;

COMMIT;

