# --- !Ups
BEGIN;

ALTER TABLE document_set ADD COLUMN version INT NOT NULL DEFAULT 1;
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

# --- !Downs

BEGIN;

ALTER TABLE document_set DROP COLUMN VERSION;
DROP TABLE document_set_versions;

COMMIT;

