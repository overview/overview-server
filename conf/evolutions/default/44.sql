# --- !Ups

BEGIN;

ALTER TABLE file_group DROP COLUMN name;

COMMIT;


# --- !Downs

BEGIN;

ALTER TABLE file_group ADD COLUMN name VARCHAR;

COMMIT;

