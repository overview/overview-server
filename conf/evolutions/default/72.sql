# --- !Ups

BEGIN;

CREATE TABLE viz (
  id BIGINT NOT NULL PRIMARY KEY,
  document_set_id BIGINT NOT NULL REFERENCES document_set (id),
  url VARCHAR NOT NULL,
  api_token VARCHAR NOT NULL,
  title VARCHAR NOT NULL,
  created_at TIMESTAMP NOT NULL,
  json_text VARCHAR NOT NULL
);

CREATE INDEX viz_document_set_id ON viz (document_set_id);


CREATE TABLE viz_object (
  id BIGINT NOT NULL PRIMARY KEY,
  viz_id BIGINT NOT NULL REFERENCES viz (id),
  indexed_long BIGINT,
  indexed_string VARCHAR,
  json_text VARCHAR NOT NULL
);

-- We _always_ specify either id or viz_id in queries
CREATE INDEX viz_object_viz_id ON viz_object (viz_id);
CREATE INDEX viz_object_viz_id_indexed_long ON viz_object (viz_id, indexed_long) WHERE indexed_long IS NOT NULL;
CREATE INDEX viz_object_viz_id_indexed_string ON viz_object (viz_id, indexed_string) WHERE indexed_string IS NOT NULL;


CREATE TABLE document_viz_object (
  document_id BIGINT NOT NULL REFERENCES document (id),
  viz_object_id BIGINT NOT NULL REFERENCES viz_object (id),
  json_text VARCHAR,
  PRIMARY KEY (document_id, viz_object_id)
);

-- Single index the other way, so we get a few index-only scans during joins
-- https://wiki.postgresql.org/wiki/Index-only_scans
CREATE INDEX document_viz_object_viz_object_id_document_id ON document_viz_object (viz_object_id, document_id);

COMMIT;

# --- !Downs

BEGIN;

DROP TABLE document_viz_object;
DROP TABLE viz_object;
DROP TABLE viz;

COMMIT;
