# --- !Ups

BEGIN;

CREATE TABLE page (
  id                         BIGSERIAL PRIMARY KEY,
  file_id                    BIGINT NOT NULL REFERENCES file(id),
  page_number                INTEGER NOT NULL,
  reference_count            INTEGER NOT NULL,
  data                       BYTEA NOT NULL
);

CREATE INDEX page_file_id ON page (file_id);

ALTER TABLE document
  ADD COLUMN page_id              BIGINT REFERENCES page (id),
  ADD COLUMN page_number          INTEGER;

CREATE INDEX document_page_id ON document (page_id);

ALTER TABLE file ADD COLUMN name VARCHAR;
UPDATE file SET name = document.title
  FROM document WHERE document.file_id = file.id;
ALTER TABLE file ALTER COLUMN name SET NOT NULL;

ALTER TABLE grouped_processed_file ALTER COLUMN contents_oid DROP NOT NULL;
ALTER TABLE grouped_processed_file
  ADD COLUMN page_data BYTEA,
  ADD COLUMN page_number INTEGER;

COMMIT;

# --- !Downs

BEGIN;

ALTER TABLE grouped_processed_file
  DROP COLUMN page_data,
  DROP COLUMN page_number;

ALTER TABLE grouped_processed_file ALTER COLUMN contents_oid SET NOT NULL; 

ALTER TABLE file DROP COLUMN name;
ALTER TABLE document 
  DROP COLUMN page_number,
  DROP COLUMN page_id;

DROP TABLE IF EXISTS page;

COMMIT;


