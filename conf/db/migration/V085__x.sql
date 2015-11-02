LOCK TABLE document;

UPDATE document
SET created_at = (SELECT created_at FROM document_set WHERE id = document.document_set_id)
WHERE created_at IS NULL;

ALTER TABLE document ALTER COLUMN created_at SET NOT NULL;

-- Then do this:
-- DROP INDEX deleteme_documents_without_dates;
