# --- !Ups

-- Optimizes indices in the node_document table.
--
-- See https://www.pivotaltracker.com/story/show/103018108

BEGIN;

-- This is CREATE INDEX IF NOT EXISTS
DO $$
  BEGIN
    IF NOT EXISTS (
      SELECT 1
      FROM pg_class c
      INNER JOIN pg_namespace n ON n.oid = c.relnamespace
      WHERE c.relname = 'node_document_document_id_node_id'
      AND n.nspname = 'public'
    ) THEN
      CREATE INDEX node_document_document_id_node_id ON node_document (document_id, node_id);;
    END IF;;
  END
$$ LANGUAGE plpgsql;

DROP INDEX IF EXISTS node_document_document_id;
DROP INDEX IF EXISTS node_document_node_id;

COMMIT;

# --- !Downs

BEGIN;

CREATE INDEX node_document_document_id ON node_document (document_id);
CREATE INDEX node_document_node_id ON node_document (node_id);
DROP INDEX node_document_document_id_node_id;

COMMIT;
