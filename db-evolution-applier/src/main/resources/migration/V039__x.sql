UPDATE document_set_creation_job SET supplied_stop_words = '' WHERE supplied_stop_words IS NULL;
ALTER TABLE document_set_creation_job ALTER COLUMN supplied_stop_words SET NOT NULL;

UPDATE document_set SET supplied_stop_words = '' WHERE supplied_stop_words IS NULL;
ALTER TABLE document_set ALTER COLUMN supplied_stop_words SET NOT NULL;
