# --- !Ups

BEGIN;

ALTER TABLE document_set_creation_job DROP CONSTRAINT document_set_creation_job_state_check;
ALTER TABLE document_set_creation_job ADD CONSTRAINT document_set_creation_job_state_check CHECK (state IN (0, 1, 2, 3, 4, 5));

ALTER TABLE document DROP COLUMN content_length;

COMMIT;

# --- !Downs

BEGIN;


ALTER TABLE document ADD COLUMN content_length BIGINT;


ALTER TABLE document_set_creation_job DROP CONSTRAINT document_set_creation_job_state_check;
ALTER TABLE document_set_creation_job ADD CONSTRAINT document_set_creation_job_state_check CHECK (state IN (0, 1, 2, 3, 4));


COMMIT;


