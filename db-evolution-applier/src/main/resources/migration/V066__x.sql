ALTER TABLE document_set_creation_job ADD COLUMN retry_attempts INTEGER DEFAULT 0;
ALTER TABLE document_set_creation_job ALTER COLUMN retry_attempts DROP DEFAULT;
