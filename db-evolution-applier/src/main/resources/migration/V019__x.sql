ALTER TABLE uploaded_file ALTER COLUMN contents_oid DROP NOT NULL;

SELECT lo_unlink(contents_oid) FROM uploaded_file 
WHERE id IN ( 
  SELECT uploaded_file_id FROM document_set 
  WHERE id NOT IN ( 
    SELECT document_set_id FROM document_set_creation_job));

UPDATE uploaded_file SET contents_oid = NULL 
WHERE id IN ( 
  SELECT uploaded_file_id FROM document_set 
  WHERE id NOT IN ( 
    SELECT document_set_id FROM document_set_creation_job)) ;
