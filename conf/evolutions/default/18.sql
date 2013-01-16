# --- !Ups

CREATE TABLE document_processing_error (
  id                        BIGSERIAL PRIMARY KEY,
  document_set_id           BIGINT NOT NULL REFERENCES document_set (id),
  text_url                  VARCHAR(511) NOT NULL,
  message		    VARCHAR NOT NULL,
  status_code		    INTEGER,
  headers		    VARCHAR
);
CREATE INDEX document_processing_error_document_set_id ON document_processing_error (document_set_id);

# --- !Downs

DROP TABLE IF EXISTS document_processing_error CASCADE;


