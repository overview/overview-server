# --- !Ups
BEGIN;

ALTER TABLE document_set_creation_job ADD COLUMN important_words VARCHAR NOT NULL DEFAULT '';
ALTER TABLE document_set ADD COLUMN important_words VARCHAR NOT NULL DEFAULT '';

COMMIT;

# --- !Downs

ALTER TABLE document_set DROP COLUMN important_words;
ALTER TABLE document_set_creation_job DROP COLUMN important_words;
