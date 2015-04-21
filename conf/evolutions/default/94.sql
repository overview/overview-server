# --- !Ups

-- New feature: auto-create views when creating document sets.
--
-- There is minimal logic here. An "autocreate" plugin gets auto-created, and
-- the order in which it is created is determined by an
-- `ORDER BY autocreate_order`. `autocreate_order` has no meaning when
-- `autocreate` is false.

BEGIN;

ALTER TABLE plugin
ADD COLUMN autocreate BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN autocreate_order INT NOT NULL DEFAULT 0;

ALTER TABLE plugin
ALTER COLUMN autocreate DROP DEFAULT;

COMMIT;

# --- !Downs

BEGIN;

ALTER TABLE plugin
DROP COLUMN autocreate,
DROP COLUMN autocreate_order;

COMMIT;
