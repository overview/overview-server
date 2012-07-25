# --- !Ups

-- Give each node a document_set_id
ALTER TABLE node ADD COLUMN document_set_id BIGINT;
UPDATE node SET document_set_id = (SELECT id FROM document_set LIMIT 1);
ALTER TABLE node ALTER COLUMN document_set_id SET NOT NULL;
ALTER TABLE node ADD CONSTRAINT fk_node_documentSet_1 FOREIGN KEY (document_set_id) REFERENCES document_set (id);
CREATE INDEX ix_node_documentSet_1 on node (document_set_id);

# --- !Downs

DROP INDEX ix_node_documentSet_1;
ALTER TABLE node DROP CONSTRAINT fk_node_documentSet_1;
ALTER TABLE node DROP COLUMN document_set_id;
