# --- !Ups

-- If you run the following queries ahead of time, this evolution will apply
-- near-instantaneously:

-- CREATE INDEX CONCURRENTLY document_document_set_id_id ON document (document_set_id, id)
-- ALTER TABLE document_set ADD COLUMN sorted_document_ids BIGINT[] NOT NULL DEFAULT '{}'
-- Several times: UPDATE document_set SET sorted_document_ids = (SELECT ARRAY_AGG(id ORDER BY title, supplied_id, page_number, id) FROM document WHERE document_set_id = document_set.id) WHERE ARRAY_LENGTH(sorted_document_ids, 1) = 0 OR ARRAY_LENGTH(sorted_document_ids, 1) IS NULL LIMIT 100

-- Note: Play evolutions split by semicolon. The escape is double-semicolon.

-- No transaction: it breaks exception handling, and we don't need it because
-- it's okay to error halfway through.

-- 1. Index document (document_set_id, id)
--    instead of just document (document_set_id). This lets us use index-only
--    scans to find all documents in a document set. (The space difference is
--    minor.)
--
--    This is a CREATE INDEX IF NOT EXISTS
DO $$
  BEGIN
    IF NOT EXISTS (
      SELECT 1
      FROM pg_class c
      INNER JOIN pg_namespace n ON n.oid = c.relnamespace
      WHERE c.relname = 'document_document_set_id_id'
      AND n.nspname = 'public'
    ) THEN
      CREATE INDEX document_document_set_id_id ON document (document_set_id, id);;
    END IF;;
  END
$$ LANGUAGE plpgsql;

DROP INDEX IF EXISTS document_document_set_id;

-- 2. Create document_set.sorted_document_ids, a cached array.
--    The ORM will be completely oblivious to this column. We will set it with
--    raw SQL only. This cache column can contain many megabytes of data, and
--    ORM callers expect document_set fetches to be fast.
--
--    This is an ADD COLUMN IF NOT EXISTS
--
--    Note: Postgres 9.4 says ARRAY_LENGTH(arr, 1) IS NULL when arr = '{}'
DO $$
  BEGIN
    ALTER TABLE document_set ADD COLUMN sorted_document_ids BIGINT[] NOT NULL DEFAULT '{}';;
  EXCEPTION
    WHEN duplicate_column THEN RAISE NOTICE 'Not adding sorted_document_ids because it already exists';;
  END
$$ LANGUAGE plpgsql;

UPDATE document_set
SET sorted_document_ids = (
  SELECT COALESCE(ARRAY_AGG(id ORDER BY title, supplied_id, page_number, id), '{}')
  FROM document
  WHERE document_set_id = document_set.id
)
WHERE COALESCE(ARRAY_LENGTH(sorted_document_ids, 1), 0) = 0;

-- we don't DROP DEFAULT: the ORM doesn't even _know_ about sorted_document_ids

# --- !Downs

BEGIN;

ALTER TABLE document_set
DROP COLUMN IF EXISTS sorted_document_ids;

CREATE INDEX document_document_set_id ON document (document_set_id);
DROP INDEX IF EXISTS document_document_set_id_id;

COMMIT;
