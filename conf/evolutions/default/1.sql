# --- !Ups

CREATE TABLE document_set (
  id                        BIGSERIAL PRIMARY KEY,
  query                     VARCHAR(1023) NOT NULL
);

CREATE TABLE document (
  id                        BIGSERIAL PRIMARY KEY,
  document_set_id           BIGINT NOT NULL REFERENCES document_set (id),
  title                     VARCHAR(255) NOT NULL,
  text_url                  VARCHAR(511) NOT NULL,
  view_url                  VARCHAR(511) NOT NULL
);
CREATE INDEX document_document_set_id ON document (document_set_id);

CREATE TABLE document_set_creation_job (
  id                        BIGSERIAL PRIMARY KEY,
  query                     VARCHAR(1023) NOT NULL,
  state                     INTEGER NOT NULL CHECK (state IN (0,1,2))
);

CREATE TABLE log_entry (
  id                        BIGSERIAL PRIMARY KEY,
  document_set_id           BIGINT NOT NULL REFERENCES document_set (id),
  username                  VARCHAR(255) NOT NULL,
  date                      TIMESTAMP NOT NULL,
  component                 VARCHAR(255) NOT NULL,
  action                    VARCHAR(255) NOT NULL,
  details                   VARCHAR(255) NOT NULL
);
CREATE INDEX log_entry_document_set_id ON log_entry (document_set_id);

CREATE TABLE node (
  id                        BIGSERIAL PRIMARY KEY,
  description               VARCHAR(255) NOT NULL,
  parent_id                 BIGINT REFERENCES node (id)
);
CREATE INDEX node_parent_id ON node (parent_id);

CREATE TABLE tag (
  id                        BIGSERIAL PRIMARY KEY,
  document_set_id           BIGINT NOT NULL REFERENCES document_set (id),
  name                      VARCHAR(255) NOT NULL,
  UNIQUE (document_set_id, name)
);
CREATE INDEX tag_document_set_id ON tag (document_set_id);

CREATE TABLE document_tag (
  document_id                    BIGINT NOT NULL REFERENCES document (id),
  tag_id                         BIGINT NOT NULL REFERENCES tag (id),
  PRIMARY KEY (document_id, tag_id)
);
CREATE INDEX document_tag_document_id ON document_tag (document_id);
CREATE INDEX document_tag_tag_id ON document_tag (tag_id);

CREATE TABLE node_document (
  node_id                        BIGINT NOT NULL REFERENCES node (id),
  document_id                    BIGINT NOT NULL REFERENCES document (id),
  PRIMARY KEY (node_id, document_id)
);
CREATE INDEX node_document_node_id ON node_document (node_id);
CREATE INDEX node_document_document_id ON node_document (document_id);

# --- !Downs

DROP TABLE IF EXISTS node_document cascade;
DROP TABLE IF EXISTS document_tag cascade;
DROP TABLE IF EXISTS tag cascade;
DROP TABLE IF EXISTS node cascade;
DROP TABLE IF EXISTS log_entry cascade;
DROP TABLE IF EXISTS document cascade;
DROP TABLE IF EXISTS document_set cascade;
DROP TABLE IF EXISTS document_set_creation_job cascade;
