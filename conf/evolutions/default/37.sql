# --- !Ups
BEGIN;

ALTER TABLE document_set_creation_job ADD COLUMN supplied_stop_words VARCHAR;

ALTER TABLE document_set ADD COLUMN supplied_stop_words VARCHAR;

COMMIT;

# --- !Downs

ALTER TABLE document_set DROP COLUMN supplied_stop_words;
ALTER TABLE document_set_creation_job DROP COLUMN supplied_stop_words;




