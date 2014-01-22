# --- !Ups


BEGIN;


ALTER TABLE document_set_creation_job
  ALTER COLUMN important_words DROP DEFAULT;

ALTER TABLE document_set
  ALTER COLUMN important_words DROP DEFAULT;

CREATE TABLE tree (
  id                      BIGINT PRIMARY KEY,
  document_set_id         BIGINT NOT NULL REFERENCES document_set (id),
  title                   VARCHAR NOT NULL,
  created_at              TIMESTAMP NOT NULL,
  document_count          INTEGER NOT NULL,
  lang                    VARCHAR NOT NULL,
  supplied_stop_words     VARCHAR NOT NULL,
  important_words         VARCHAR NOT NULL
);
CREATE INDEX tree_document_set_id ON tree (document_set_id);

INSERT INTO tree (id, document_set_id, title, created_at, document_count, lang, supplied_stop_words, important_words)
  SELECT node.id, document_set.id,
         document_set.title, document_set.created_at, document_set.document_count,
         document_set.lang, document_set.supplied_stop_words, document_set.important_words 
    FROM node 
    INNER JOIN document_set ON (node.document_set_id = document_set.id) WHERE node.parent_id IS NULL;


COMMIT;

# --- !Downs


BEGIN;

DROP TABLE IF EXISTS tree;

ALTER TABLE document_set
  ALTER COLUMN important_words SET DEFAULT '';

ALTER TABLE document_set_creation_job
  ALTER COLUMN important_words SET DEFAULT '';


COMMIT;

