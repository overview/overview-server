ALTER TABLE "user"
  ADD COLUMN tree_tooltips_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE "user"
  ALTER COLUMN tree_tooltips_enabled DROP DEFAULT;
