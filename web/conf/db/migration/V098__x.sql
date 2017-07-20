-- Remove a NOT NULL from the database schema.
--
-- https://www.pivotaltracker.com/story/show/93912920
--
-- At the time of authoring, the "file" table is ~1M rows (<300MB) on
-- production. That's small. On staging, the entire transaction took 35
-- seconds. Furthermore, the evolution can be applied multiple times, so
-- we can run it on production before we even deploy.

LOCK TABLE "file";

UPDATE "file"
SET contents_sha1 = E'\\x0000000000000000000000000000000000000000'::bytea
WHERE contents_sha1 IS NULL;

ALTER TABLE "file"
ALTER COLUMN contents_sha1
SET NOT NULL;
