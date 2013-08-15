# --- !Ups
BEGIN;

UPDATE document_set_creation_job SET supplied_stop_words = '' WHERE supplied_stop_words IS NULL;
ALTER TABLE document_set_creation_job ALTER COLUMN supplied_stop_words SET NOT NULL;

UPDATE document_set SET supplied_stop_words = '' WHERE supplied_stop_words IS NULL;
ALTER TABLE document_set ALTER COLUMN supplied_stop_words SET NOT NULL;

COMMIT;

# --- !Downs

BEGIN;

ALTER TABLE document_set ALTER COLUMN supplied_stop_words DROP NOT NULL;
UPDATE document_set_creation_job SET supplied_stop_words = NULL WHERE supplied_stop_words = '';

ALTER TABLE document_set_creation_job ALTER COLUMN supplied_stop_words DROP NOT NULL;
UPDATE document_set SET supplied_stop_words = NULL  WHERE supplied_stop_words = '';

COMMIT;

