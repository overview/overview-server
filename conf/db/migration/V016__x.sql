ALTER TABLE document_set_creation_job DROP CONSTRAINT document_set_creation_job_state_check;
ALTER TABLE document_set_creation_job ADD CONSTRAINT document_set_creation_job_state_check CHECK (state IN (0,1,2,3));
