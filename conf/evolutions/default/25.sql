# --- !Ups

ALTER TABLE document_set ADD COLUMN document_count INT;
UPDATE document_set SET document_count = 
  (SELECT COUNT(*) FROM document
     WHERE document.document_set_id = document_set.id);

ALTER TABLE document_set ALTER COLUMN document_count SET NOT NULL;

# --- !Downs

ALTER TABLE document_set DROP COLUMN document_count;
