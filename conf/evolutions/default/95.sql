# --- !Ups

-- Use a sequence for view IDs.
--
-- We previously made View IDs correspond to their DocumentSet IDs. That was
-- consistent with Document IDs and Node IDs, which assign themselves IDs
-- because sequences are slow. But View creation is far less frequent, so we
-- can nix code by just using a sequence.

BEGIN;

CREATE SEQUENCE view_id_seq;

SELECT SETVAL('view_id_seq', MAX(id)) FROM "view";

ALTER TABLE "view"
ALTER COLUMN id SET DEFAULT nextval('view_id_seq');

COMMIT;

# --- !Downs

BEGIN;

ALTER TABLE "view"
ALTER COLUMN id DROP DEFAULT;

DROP SEQUENCE view_id_seq;

COMMIT;
