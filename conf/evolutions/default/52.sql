# --- !Ups


BEGIN;

INSERT INTO document_set_creation_job_type (id, name) VALUES (5, 'Recluster');

COMMIT;

# --- !Downs


BEGIN;

DELETE FROM document_set_creation_job_type WHERE id = 5;

COMMIT;

