# --- !Ups


BEGIN;

ALTER TABLE node ADD COLUMN tree_id BIGINT REFERENCES tree (id);
CREATE INDEX node_tree_id ON node (tree_id);

UPDATE node SET tree_id = tree.id
  FROM tree WHERE tree.document_set_id = node.document_set_id;

ALTER TABLE node ALTER COLUMN tree_id SET NOT NULL;

-- ALTER TABLE node DROP COLUMN document_set_id;

COMMIT;

# --- !Downs


BEGIN;

-- ALTER TABLE node ADD COLUMN document_set_id BIGINT REFERENCES document_set (id);
-- CREATE INDEX node_document_set_id ON node (document_set_id);

-- UPDATE node SET document_set_id = tree.document_set_id
--  FROM tree WHERE node.tree_id = tree.id;

-- ALTER TABLE node ALTER COLUMN document_set_id SET NOT NULL;

ALTER TABLE node DROP COLUMN tree_id;

COMMIT;

