CREATE TABLE csv_import (
  id BIGSERIAL,
  document_set_id BIGINT NOT NULL REFERENCES document_set (id),
  filename VARCHAR NOT NULL,
  charset VARCHAR NOT NULL,
  lang CHAR(2) NOT NULL,
  loid OID,
  n_bytes BIGINT NOT NULL,
  n_bytes_processed BIGINT NOT NULL,
  cancelled BOOLEAN NOT NULL,
  estimated_completion_time TIMESTAMP WITH TIME ZONE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

COMMENT ON COLUMN csv_import.loid IS 'We may set this to NULL after processing, to save space.';

CREATE INDEX ON csv_import (document_set_id);
CREATE INDEX pending_csv_imports ON csv_import (id) WHERE n_bytes_processed < n_bytes;

INSERT INTO csv_import (
  document_set_id,
  filename,
  charset,
  lang,
  loid,
  n_bytes,
  n_bytes_processed,
  cancelled,
  estimated_completion_time,
  created_at
)
SELECT
  ds.id,
  ds.title,
  'utf-8',
  'en',
  NULL,
  uf.size,
  uf.size,
  FALSE,
  NULL,
  ds.created_at
FROM document_set ds
INNER JOIN uploaded_file uf ON ds.uploaded_file_id = uf.id;

ALTER TABLE document_set DROP COLUMN uploaded_file_id;

DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN (SELECT contents_oid AS loid FROM document_set_creation_job WHERE contents_oid IS NOT NULL) LOOP
    BEGIN
      PERFORM lo_unlink(r.loid);
    EXCEPTION
      WHEN undefined_object THEN NULL;
    END;
  END LOOP;
END$$;
DELETE FROM document_set_creation_job WHERE contents_oid IS NOT NULL;
ALTER TABLE document_set_creation_job DROP COLUMN contents_oid;
