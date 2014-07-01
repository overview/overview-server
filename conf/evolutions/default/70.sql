# --- !Ups

BEGIN;

-- Instead of node.tree_id, we'll have node.root_id and tree.root_node_id.
-- That means Node no longer depends on Tree.
ALTER TABLE node ADD COLUMN root_id BIGINT REFERENCES node (id);
ALTER TABLE tree ADD COLUMN root_node_id BIGINT REFERENCES node (id);

UPDATE node
SET root_id = roots.node_id
FROM (
  SELECT tree_id, id AS node_id
  FROM node
  WHERE parent_id IS NULL
) roots
WHERE node.tree_id = roots.tree_id;

UPDATE tree
SET root_node_id = roots.node_id
FROM (
  SELECT tree_id, id AS node_id
  FROM node
  WHERE parent_id IS NULL
) roots
WHERE tree.id = roots.tree_id;

ALTER TABLE node ALTER COLUMN root_id SET NOT NULL;
ALTER TABLE tree ALTER COLUMN root_node_id SET NOT NULL;

ALTER TABLE node DROP COLUMN tree_id;

CREATE INDEX node_root_id ON node (root_id); -- for foreign key
CREATE INDEX tree_root_node_id ON tree (root_node_id); -- for foreign key

-- When we're creating nodes, the tree hasn't been written yet. (We can't write
-- the tree first because it refers to the root node.)
--
-- Store the root node, so we can delete all nodes before reclustering.
CREATE TABLE document_set_creation_job_node (
  document_set_creation_job_id BIGINT NOT NULL REFERENCES document_set_creation_job (id),
  node_id BIGINT NOT NULL REFERENCES node (id),
  PRIMARY KEY (document_set_creation_job_id, node_id)
);
CREATE INDEX document_set_creation_job_node_document_set_creation_job_id ON document_set_creation_job_node (document_set_creation_job_id);
CREATE UNIQUE INDEX document_set_creation_job_node_node_id ON document_set_creation_job_node (node_id);

COMMIT;

# --- !Downs

BEGIN;

DROP TABLE document_set_creation_job_node;

ALTER TABLE node ADD COLUMN tree_id BIGINT REFERENCES tree (id);

UPDATE node
SET tree_id = tree.id
FROM tree
WHERE tree.root_node_id = node.root_id;

CREATE INDEX node_tree_id ON node (tree_id);

ALTER TABLE tree DROP COLUMN root_node_id;
ALTER TABLE node DROP COLUMN root_id;

COMMIT;
