ALTER TABLE document_set_creation_job ADD COLUMN important_words VARCHAR NOT NULL DEFAULT '';
ALTER TABLE document_set ADD COLUMN important_words VARCHAR NOT NULL DEFAULT '';
