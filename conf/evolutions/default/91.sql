# --- !Ups

-- This is a reversal of 41.sql.
--
-- We built something complicated to work around a simple problem: document
-- sets weren't indexed. Since then, we implemented the simple solution: index
-- the document sets.
--
-- Our code no longer needs this version number, and we can't say for sure
-- we'll need it again in the future.

BEGIN;

ALTER TABLE document_set DROP COLUMN version;
DROP TABLE document_set_versions;

COMMIT;

# --- !Downs

BEGIN;

ALTER TABLE document_set ADD COLUMN version INT NOT NULL DEFAULT 2;
ALTER TABLE document_set ALTER COLUMN version DROP DEFAULT;

CREATE TABLE document_set_versions (
  version                     INT NOT NULL,
  description                 VARCHAR(1023) NOT NULL
);

-- Just used to describe document set versions
INSERT INTO document_set_versions (version, description) VALUES
  (1, 'Initial version'),
  (2, 'Searchable document sets');

COMMIT;
