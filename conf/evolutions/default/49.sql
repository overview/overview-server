# --- !Ups


BEGIN;

CREATE TABLE file (
  id                 BIGSERIAL PRIMARY KEY,
  reference_count    INTEGER NOT NULL,
  contents_oid       OID NOT NULL
);

ALTER TABLE document ADD COLUMN file_id BIGINT REFERENCES file (id);

INSERT INTO file (reference_count, contents_oid)
  SELECT 1, contents_oid FROM document WHERE contents_oid IS NOT NULL;

UPDATE document SET file_id = file.id 
  FROM file WHERE document.contents_oid = file.contents_oid;

ALTER TABLE document DROP COLUMN contents_oid;
 
COMMIT;

# --- !Downs


BEGIN;

ALTER TABLE document ADD COLUMN contents_oid OID;

UPDATE document set contents_oid = file.contents_oid
  FROM file WHERE document.file_id = file.id;

ALTER TABLE document DROP COLUMN file_id;

DROP TABLE IF EXISTS file;

COMMIT;

