ALTER TABLE csv_import ADD COLUMN n_documents INT;

UPDATE csv_import
SET n_documents = (SELECT document_count FROM document_set WHERE document_set.id = csv_import.document_set_id);

ALTER TABLE csv_import ALTER COLUMN n_documents SET NOT NULL;
