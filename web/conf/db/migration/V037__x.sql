ALTER TABLE document_set_creation_job ADD COLUMN supplied_stop_words VARCHAR;

ALTER TABLE document_set ADD COLUMN supplied_stop_words VARCHAR;
