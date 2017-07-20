CREATE TABLE document_set_reindex_job (
  id SERIAL PRIMARY KEY,
  document_set_id BIGINT NOT NULL REFERENCES document_set(id),
  last_requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
  started_at TIMESTAMP WITH TIME ZONE,
  progress DOUBLE PRECISION NOT NULL
);
COMMENT ON COLUMN document_set_reindex_job.last_requested_at IS 'The last time somebody tried to open this document set, (which indicates its priority)';
CREATE INDEX ON document_set_reindex_job (document_set_id); -- used when opening docset
CREATE INDEX ON document_set_reindex_job (last_requested_at); -- used when finding next docset to reindex

INSERT INTO document_set_reindex_job (document_set_id, last_requested_at, started_at, progress)
SELECT id, created_at, NULL, 0.0
FROM document_set
WHERE deleted IS FALSE;
