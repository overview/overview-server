# --- !Ups

ALTER TABLE document_set ADD COLUMN public BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE document_set ALTER COLUMN public DROP DEFAULT;


# --- !Downs

ALTER TABLE document_set DROP COLUMN public;

