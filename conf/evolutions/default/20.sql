# --- !Ups

CREATE TYPE document_set_creation_job_type AS ENUM ('CsvImportJob', 'DocumentCloudJob', 'CloneJob'); 


ALTER TABLE document_set_creation_job ADD COLUMN type document_set_creation_job_type;

UPDATE document_set_creation_job SET type =
  (SELECT CASE
    WHEN type = 'CsvImportDocumentSet'::document_set_type THEN 'CsvImportJob':: document_set_creation_job_type
    WHEN type = 'DocumentCloudDocumentSet'::document_set_type THEN 'DocumentCloudJob':: document_set_creation_job_type
   END FROM document_set
   WHERE document_set.id = document_set_creation_job.document_set_id);






# --- !Downs


ALTER TABLE document_set_creation_job DROP COLUMN type;
DROP TYPE document_set_creation_job_type;
