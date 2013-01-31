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

ALTER TABLE document_set_creation_job ADD COLUMN source_document_set_id BIGINT;

ALTER TABLE document_set_creation_job ADD CONSTRAINT document_set_creation_job_documentcloud_job_type_check
  CHECK (type <> 'DocumentCloudJob' OR (
    source_document_set_id IS NULL
    AND contents_oid IS NULL));


ALTER TABLE document_set_creation_job ADD CONSTRAINT document_set_creation_job_csv_import_job_type_check
  CHECK (type <> 'CsvImportJob' OR (
    contents_oid IS NOT NULL
    AND source_document_set_id IS NULL
    AND documentcloud_username IS NULL
    AND documentcloud_password IS NULL));


ALTER TABLE document_set_creation_job ADD CONSTRAINT document_set_creation_job_clone_job_type_check
  CHECK (type <> 'CloneJob' OR (
    source_document_set_id IS NOT NULL
    AND contents_oid IS NULL
    AND documentcloud_username IS NULL
    AND documentcloud_password IS NULL));


# --- !Downs


ALTER TABLE document_set_creation_job DROP CONSTRAINT document_set_creation_job_clone_job_type_check;
ALTER TABLE document_set_creation_job DROP CONSTRAINT document_set_creation_job_csv_import_job_type_check;
ALTER TABLE document_set_creation_job DROP CONSTRAINT document_set_creation_job_documentcloud_job_type_check;

ALTER TABLE document_set_creation_job DROP COLUMN source_document_set_id;

ALTER TABLE document_set_creation_job DROP COLUMN type;
DROP TYPE document_set_creation_job_type;
