ALTER TABLE node ADD COLUMN tree_id BIGINT REFERENCES tree (id);
CREATE INDEX node_tree_id ON node (tree_id);

UPDATE node SET tree_id = tree.id
  FROM tree WHERE tree.document_set_id = node.document_set_id;

ALTER TABLE node ALTER COLUMN tree_id SET NOT NULL;

ALTER TABLE node DROP COLUMN document_set_id;
