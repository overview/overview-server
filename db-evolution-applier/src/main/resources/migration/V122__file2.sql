CREATE TABLE file2 (
  id BIGSERIAL NOT NULL PRIMARY KEY,
  root_file2_id BIGINT REFERENCES file2 (id),
  parent_file2_id BIGINT REFERENCES file2 (id),
  index_in_parent INT NOT NULL,
  filename VARCHAR NOT NULL,
  content_type VARCHAR NOT NULL,
  language_code VARCHAR NOT NULL,
  metadata_json_utf8 BYTEA NOT NULL,
  pipeline_options_json_utf8 BYTEA NOT NULL,
  blob_location VARCHAR,
  blob_n_bytes INT,
  blob_sha1 BYTEA,
  thumbnail_blob_location VARCHAR,
  thumbnail_blob_n_bytes INT,
  thumbnail_content_type VARCHAR,
  "text" TEXT,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  written_at TIMESTAMP WITH TIME ZONE,
  processed_at TIMESTAMP WITH TIME ZONE,
  n_children INT,
  processing_error VARCHAR,
  ingested_at TIMESTAMP WITH TIME ZONE,
  CONSTRAINT state_written CHECK (written_at IS NULL OR (blob_location IS NOT NULL AND blob_n_bytes IS NOT NULL)),
  CONSTRAINT dedup UNIQUE (parent_file2_id, index_in_parent)
);

CREATE INDEX ON file2 (root_file2_id);
CREATE INDEX ON file2 (parent_file2_id);

CREATE TABLE document_set_file2 (
  document_set_id BIGINT NOT NULL REFERENCES document_set (id),
  file2_id BIGINT NOT NULL REFERENCES file2 (id),
  PRIMARY KEY (document_set_id, file2_id)
);
CREATE INDEX ON document_set_file2 (file2_id); -- speed up DELETE FROM file2's FK check

ALTER TABLE document_processing_error
  ADD COLUMN file2_id BIGINT REFERENCES file2 (id);
CREATE INDEX ON document_processing_error (file2_id); -- speed up DELETE FROM file2's FK check

ALTER TABLE document
  ADD COLUMN file2_id BIGINT REFERENCES file2 (id);
CREATE INDEX ON document (file2_id); -- speed up DELETE FROM file2's FK check

ALTER TABLE grouped_file_upload
  ADD COLUMN file2_id BIGINT REFERENCES file2 (id);
CREATE INDEX ON grouped_file_upload (file2_id); -- speed up DELETE FROM file2's FK check
