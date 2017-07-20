TRUNCATE TABLE document_set_creation_job;
DROP INDEX IF EXISTS document_set_creation_job_user_id;
ALTER TABLE document_set_creation_job DROP user_id;
ALTER TABLE document_set_creation_job DROP query;
ALTER TABLE document_set_creation_job ADD document_set_id BIGINT NOT NULL REFERENCES document_set (id);
ALTER TABLE document_set_creation_job ADD fraction_complete FLOAT NOT NULL DEFAULT 0.0 CHECK (fraction_complete >= 0.0 AND fraction_complete <= 1.0);
ALTER TABLE document_set_creation_job ADD status_description TEXT NOT NULL DEFAULT '';
CREATE INDEX document_set_creation_job_document_set_id ON document_set_creation_job (document_set_id);
