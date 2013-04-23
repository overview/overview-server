# --- !Ups

ALTER TABLE document_set_creation_job ADD COLUMN split_documents BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE document_set_creation_job ALTER COLUMN split_documents DROP DEFAULT;

# --- !Downs

ALTER TABLE document_set_creation_job DROP COLUMN split_documents;

