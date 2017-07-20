ALTER TABLE document_set_creation_job ADD COLUMN user_id BIGINT REFERENCES "user" (id);
UPDATE document_set_creation_job SET user_id = "user".id FROM  "user" WHERE email = 'admin@overview-project.org';
ALTER TABLE document_set_creation_job ALTER COLUMN user_id SET  NOT NULL;

CREATE INDEX document_set_creation_job_user_id ON document_set_creation_job (user_id);
