-- This foreign key makes it harder to do things.

ALTER TABLE dangling_node
DROP CONSTRAINT dangling_node_root_node_id_fkey;
