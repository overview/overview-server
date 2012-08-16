# --- !Ups

ALTER TABLE document_set_creation_job ADD COLUMN user_id BIGINT NOT NULL REFERENCES "user" (id);
CREATE INDEX document_set_creation_job_user_id ON document_set_creation_job (user_id);
CREATE SEQUENCE s_document_set_creation_job_id ;

# --- !Downs

DROP SEQUENCE IF EXISTS s_document_set_creation_job_id;
DROP INDEX IF EXISTS document_set_creation_job_user_id CASCADE;
ALTER TABLE document_set_creation_job DROP COLUMN user_id CASCADE;