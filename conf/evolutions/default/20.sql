# --- !Ups

CREATE TYPE document_set_creation_job_type AS ENUM ('CsvImportJob', 'DocumentCloudJob', 'CloneJob'); 


ALTER TABLE document_set_creation_job ADD COLUMN type document_set_creation_job_type;

UPDATE document_set_creation_job SET type =
  (SELECT CASE
    WHEN type = 'CsvImportDocumentSet'::document_set_type THEN 'CsvImportJob':: document_set_creation_job_type
    WHEN type = 'DocumentCloudDocumentSet'::document_set_type THEN 'DocumentCloudJob':: document_set_creation_job_type
   END FROM document_set
   WHERE document_set.id = document_set_creation_job.document_set_id);

ALTER TABLE document_set_creation_job ALTER COLUMN type SET NOT NULL;

ALTER TABLE upload ADD COLUMN contents_oid OID NOT NULL;




# --- !Downs

ALTER TABLE upload DROP COLUMN contents_oid;
ALTER TABLE document_set_creation_job DROP COLUMN type;
DROP TYPE document_set_creation_job_type;
