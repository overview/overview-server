# --- !Ups

ALTER TABLE upload ADD COLUMN contents_oid OID NOT NULL;

UPDATE upload SET contents_oid = uploaded_file.contents_oid 
  FROM uploaded_file
  WHERE upload.uploaded_file_id = uploaded_file.id;

ALTER TABLE document_set_creation_job ADD COLUMN contents_oid OID;
UPDATE document_set_creation_job SET contents_oid = uploaded_file.contents_oid 
  FROM uploaded_file, document_set
  WHERE document_set_creation_job.document_set_id = document_set.id
    AND document_set.uploaded_file_id = uploaded_file.id;

ALTER TABLE uploaded_file DROP COLUMN contents_oid;



# --- !Downs

ALTER TABLE uploaded_file ADD COLUMN contents_oid OID;
UPDATE uploaded_file SET contents_oid = document_set_creation_job.contents_oid
  FROM document_set_creation_job, document_set
  WHERE document_set.uploaded_file_id = uploaded_file.id 
    AND document_set.id = document_set_creation_job.document_set_id;


ALTER TABLE document_set_creation_job DROP COLUMN contents_oid;
ALTER TABLE upload DROP COLUMN contents_oid;

