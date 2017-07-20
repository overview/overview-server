CREATE TABLE temp_document_set_file (
  document_set_id     BIGINT NOT NULL, 
  file_id             BIGINT NOT NULL,
  PRIMARY KEY (document_set_id, file_id)
);
