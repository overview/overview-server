# --- !Ups

BEGIN;

ALTER TABLE "user"
  ADD COLUMN tree_tooltips_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE "user"
  ALTER COLUMN tree_tooltips_enabled DROP DEFAULT;

COMMIT;

# --- !Downs

BEGIN;

ALTER TABLE "user"
  DROP COLUMN tree_tooltips_enabled;

COMMIT;
