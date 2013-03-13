# --- !Ups

ALTER TABLE document_set ADD COLUMN document_processing_error_count INT;
UPDATE document_set SET document_processing_error_count = 
  (SELECT COUNT(*) FROM document_processing_error
     WHERE document_processing_error.document_set_id = document_set.id);

ALTER TABLE document_set ALTER COLUMN document_processing_error_count SET NOT NULL;

# --- !Downs

ALTER TABLE document_set DROP COLUMN document_processing_error_count;
