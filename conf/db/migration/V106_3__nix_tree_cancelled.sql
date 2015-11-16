-- While documentset-worker *should* be the process deleting trees, it is
-- currently the worker. There's no use for tree.cancelled: the client
-- simply deletes the tree.

ALTER TABLE tree DROP COLUMN cancelled;
