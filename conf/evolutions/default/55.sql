# --- !Ups


BEGIN;

ALTER TABLE document_set_creation_job ADD COLUMN tag_id BIGINT;
CREATE INDEX document_set_creation_job_tag_id ON document_set_creation_job (tag_id);

ALTER TABLE document_set_creation_job ADD COLUMN tree_description VARCHAR;

COMMIT;

# --- !Downs


BEGIN;

ALTER TABLE document_set_creation_job DROP COLUMN tree_description;
ALTER TABLE document_set_creation_job DROP COLUMN tag_id;

COMMIT;

