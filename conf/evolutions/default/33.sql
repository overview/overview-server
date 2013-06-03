# --- !Ups

CREATE INDEX node_document_set_roots ON node (document_set_id) WHERE parent_id IS NULL;

# --- !Downs

DROP INDEX node_document_set_roots;
