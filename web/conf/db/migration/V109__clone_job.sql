CREATE TABLE clone_job (
  id SERIAL,
  source_document_set_id BIGINT NOT NULL,
  destination_document_set_id BIGINT NOT NULL REFERENCES document_set (id),
  step_number SMALLINT NOT NULL,
  cancelled BOOLEAN NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ON clone_job (destination_document_set_id);

DELETE FROM document_set_creation_job
WHERE source_document_set_id IS NOT NULL;

ALTER TABLE document_set_creation_job
DROP COLUMN source_document_set_id;
