# --- !Ups

BEGIN;

CREATE TABLE search_result_state (
  id                             INT NOT NULL PRIMARY KEY,
  name                           VARCHAR NOT NULL
);

INSERT INTO search_result_state (id, name) VALUES
  (1, 'Complete'),
  (2, 'InProgress'),
  (3, 'Error');

CREATE TABLE search_result (
  id                             BIGSERIAL PRIMARY KEY NOT NULL,
  state                          INT NOT NULL,         
  document_set_id                BIGINT NOT NULL references document_set (id),
  query                          VARCHAR NOT NULL
);

CREATE TABLE document_search_result (
  document_id                    BIGINT NOT NULL references document (id),
  search_result_id               BIGINT NOT NULL references search_result (id),
  PRIMARY KEY (search_result_id, document_id)
);
CREATE INDEX document_search_result_document_id ON document_search_result (document_id);
CREATE INDEX document_search_result_search_result_id ON document_search_result (search_result_id);

COMMIT;

# --- !Downs

BEGIN;

DROP TABLE IF EXISTS document_search_result CASCADE;
DROP TABLE IF EXISTS search_result CASCADE;
DROP TABLE IF EXISTS search_result_state CASCADE;

COMMIT;



