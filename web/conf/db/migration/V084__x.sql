ALTER TABLE document ADD COLUMN created_at TIMESTAMP;

-- Then do this:
-- UPDATE document SET created_at = (SELECT created_at FROM document_set WHERE id = document.document_set_id);
-- CREATE INDEX deleteme_documents_without_dates ON document (id) WHERE created_at IS NULL;
