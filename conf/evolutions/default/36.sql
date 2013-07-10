# --- !Ups
BEGIN;

ALTER TABLE document_set_creation_job ADD COLUMN lang VARCHAR NOT NULL DEFAULT 'en';
ALTER TABLE document_set_creation_job ALTER COLUMN lang DROP DEFAULT;

ALTER TABLE document_set ADD COLUMN lang VARCHAR NOT NULL DEFAULT 'en';
ALTER TABLE document_set ALTER COLUMN lang DROP DEFAULT;

COMMIT;

# --- !Downs

ALTER TABLE document_set DROP COLUMN lang;
ALTER TABLE document_set_creation_job DROP COLUMN lang;




