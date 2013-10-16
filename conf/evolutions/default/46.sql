# --- !Ups

BEGIN;

ALTER TABLE grouped_processed_file ADD COLUMN size BIGINT NOT NULL DEFAULT(0);
ALTER TABLE grouped_processed_file ALTER COLUMN size DROP DEFAULT;

UPDATE grouped_processed_file SET size = grouped_file_upload.size
  FROM grouped_file_upload
  WHERE grouped_processed_file.contents_oid = grouped_file_upload.contents_oid;


ALTER TABLE document ADD COLUMN contents_oid OID;
ALTER TABLE document ADD COLUMN content_length BIGINT;

COMMIT;


# --- !Downs

BEGIN;

ALTER TABLE document DROP COLUMN content_length;
ALTER TABLE document DROP COLUMN contents_oid;
ALTER TABLE grouped_processed_File DROP COLUMN size;

COMMIT;

