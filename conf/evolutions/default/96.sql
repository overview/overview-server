# --- !Ups

-- Store a sha1 on the file table.
--
-- The sha1 will help us detect when somebody is uploading a duplicate file.
-- Eventually, we'll calculate all the existing files' sha1s and set the column
-- to NOT NULL. That's https://www.pivotaltracker.com/story/show/93912920
--
-- We index the column, to speed up HEAD /documentsets/:id/files/:sha1

BEGIN;

ALTER TABLE "file"
ADD COLUMN contents_sha1 BYTEA CHECK (LENGTH(contents_sha1) = 20);

CREATE INDEX file_contents_sha1
ON "file" (contents_sha1)
WHERE contents_sha1 IS NOT NULL;

COMMIT;

# --- !Downs

BEGIN;

ALTER TABLE "file"
DROP COLUMN contents_sha1;

COMMIT;
