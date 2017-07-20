ALTER TABLE upload ADD COLUMN contents_oid OID;

UPDATE upload SET contents_oid = uploaded_file.contents_oid 
  FROM uploaded_file
  WHERE upload.uploaded_file_id = uploaded_file.id;

ALTER TABLE upload ALTER COLUMN contents_oid SET NOT NULL;

ALTER TABLE document_set_creation_job ADD COLUMN contents_oid OID;
UPDATE document_set_creation_job SET contents_oid = uploaded_file.contents_oid 
  FROM uploaded_file, document_set
  WHERE document_set_creation_job.document_set_id = document_set.id
    AND document_set.uploaded_file_id = uploaded_file.id;

ALTER TABLE uploaded_file DROP COLUMN contents_oid;
