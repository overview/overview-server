# --- !Ups

BEGIN;

ALTER TABLE "file"
  ALTER COLUMN contents_size SET NOT NULL,
  ALTER COLUMN view_size SET NOT NULL;

COMMIT;

# --- !Downs

BEGIN;

ALTER TABLE "file"
  ALTER COLUMN contents_size DROP NOT NULL,
  ALTER COLUMN view_size DROP NOT NULL;

COMMIT;
