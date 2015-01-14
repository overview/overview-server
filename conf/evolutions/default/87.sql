# --- !Ups

BEGIN;

LOCK TABLE "file";

ALTER TABLE "file"
  ADD COLUMN contents_location VARCHAR,
  ADD COLUMN view_location VARCHAR;

UPDATE "file"
SET
  contents_location = 'pglo:' || contents_oid,
  view_location = 'pglo:' || view_oid;

ALTER TABLE "file"
  ALTER COLUMN contents_location SET NOT NULL,
  ALTER COLUMN view_location SET NOT NULL,
  DROP COLUMN contents_oid,
  DROP COLUMN view_oid;

COMMIT;

# --- !Downs

BEGIN;

ALTER TABLE "file"
  ADD COLUMN contents_oid BIGINT,
  ADD COLUMN view_oid BIGINT;

-- SUBSTR()'s "from" is 1-base, not 0.
UPDATE "file"
SET
  contents_oid = SUBSTR(contents_location, 6)::BIGINT,
  view_oid = SUBSTR(view_location, 6)::BIGINT;

ALTER TABLE "file"
  ALTER COLUMN contents_oid SET NOT NULL,
  ALTER COLUMN view_oid SET NOT NULL,
  DROP COLUMN contents_location,
  DROP COLUMN view_location;

COMMIT;
