# --- !Ups


BEGIN;

CREATE TABLE document_set_creation_job_tree (
  document_set_creation_job_id     BIGINT NOT NULL REFERENCES document_set_creation_job (id),
  tree_id                           BIGINT NOT NULL REFERENCES tree (id),
  PRIMARY KEY (document_set_creation_job_id, tree_id)
);
CREATE INDEX document_set_creation_job_tree_document_set_creation_job_id ON document_set_creation_job_tree (document_set_creation_job_id);
CREATE UNIQUE INDEX document_set_creation_job_tree_tree_id ON document_set_creation_job_tree (tree_id);

COMMIT;

# --- !Downs


BEGIN;

DROP TABLE IF EXISTS document_set_creation_job_tree CASCADE;

COMMIT;

