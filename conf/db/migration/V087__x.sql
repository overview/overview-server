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
