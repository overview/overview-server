# --- !Ups

BEGIN;

CREATE TABLE file_group (
  id               BIGSERIAL PRIMARY KEY,
  name             VARCHAR,
  user_email       VARCHAR(255) NOT NULL,
  state            INTEGER NOT NULL
);
CREATE INDEX file_group_user_email ON file_group (user_email);

CREATE TABLE file_job_state (
  id                INTEGER NOT NULL,
  description       VARCHAR NOT NULL
);

INSERT INTO file_job_state (id, description) VALUES
  (1, 'Complete'),
  (2, 'InProgress');



CREATE TABLE grouped_processed_file (
  id                   BIGSERIAL PRIMARY KEY,
  file_group_id        BIGINT NOT NULL references file_group (id),
  content_type         VARCHAR NOT NULL,
  name                 VARCHAR NOT NULL,
  error_message        VARCHAR,
  text                 VARCHAR,
  contents_oid         OID NOT NULL
);
CREATE INDEX grouped_processed_file_file_group_id ON grouped_processed_file (file_group_id);

CREATE TABLE grouped_file_upload (
  id                    BIGSERIAL PRIMARY KEY,
  file_group_id         BIGINT NOT NULL REFERENCES file_group (id),
  guid                  UUID NOT NULL,
  content_type          VARCHAR NOT NULL,
  name                  VARCHAR NOT NULL,
  size                  BIGINT NOT NULL,
  last_modified_date    VARCHAR(255) NOT NULL,
  uploaded_size		BIGINT NOT NULL,
  contents_oid          OID NOT NULL
);
CREATE INDEX grouped_file_upload_file_group_id ON grouped_file_upload (file_group_id);

ALTER TABLE document_set_creation_job ADD COLUMN file_group_id BIGINT REFERENCES file_group(id);
CREATE INDEX document_set_creation_job_file_group_id ON document_set_creation_job (file_group_id);

INSERT INTO document_set_creation_job_type (id, name) VALUES (4, 'FileUpload');
ALTER TABLE document_set_creation_job ADD CONSTRAINT document_set_creation_job_file_upload_job_type_check
  CHECK (type <> 4 OR file_group_id IS NOT NULL);

ALTER TABLE document_set_creation_job DROP CONSTRAINT document_set_creation_job_state_check;
ALTER TABLE document_set_creation_job ADD CONSTRAINT document_set_creation_job_state_check CHECK (state IN (0,1,2,3,4));

COMMIT;



# --- !Downs

BEGIN;

DELETE FROM document_set_creation_job WHERE document_set_creation_job.state = 4;
ALTER TABLE document_set_creation_job DROP CONSTRAINT document_set_creation_job_state_check;
ALTER TABLE document_set_creation_job ADD CONSTRAINT document_set_creation_job_state_check CHECK (state IN (0,1,2,3));

ALTER TABLE document_set_creation_job DROP CONSTRAINT document_set_creation_job_file_upload_job_type_check;
DELETE FROM document_set_creation_job_type WHERE id = 4;
ALTER TABLE document_set_creation_job DROP COLUMN file_group_id;

DROP TABLE IF EXISTS grouped_file_upload CASCADE;
DROP TABLE IF EXISTS grouped_process_file CASCADE;
DROP TABLE IF EXISTS file_job_state CASCADE;
DROP TABLE IF EXISTS file_group CASCADE;

COMMIT;

