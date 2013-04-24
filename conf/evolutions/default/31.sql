# --- !Ups

BEGIN;

LOCK TABLE document_set_creation_job;

ALTER TABLE document_set_creation_job ADD COLUMN type2 INT;

UPDATE document_set_creation_job SET type2 = CASE "type"
  WHEN 'DocumentCloudJob'::document_set_creation_job_type THEN 1
  WHEN 'CsvImportJob'::document_set_creation_job_type THEN 2
  WHEN 'CloneJob'::document_set_creation_job_type THEN 3
  END;
ALTER TABLE document_set_creation_job ALTER COLUMN type2 SET NOT NULL;

ALTER TABLE document_set_creation_job DROP COLUMN "type"; -- drop constraints--we'll re-add them
ALTER TABLE document_set_creation_job RENAME COLUMN type2 TO "type";

DROP TYPE document_set_creation_job_type;
CREATE TABLE document_set_creation_job_type (id INT NOT NULL PRIMARY KEY, name VARCHAR NOT NULL);
INSERT INTO document_set_creation_job_type (id, name) VALUES (1, 'DocumentCloud');
INSERT INTO document_set_creation_job_type (id, name) VALUES (2, 'CsvUpload');
INSERT INTO document_set_creation_job_type (id, name) VALUES (3, 'Clone');

-- re-add constraints from 21.sql
ALTER TABLE document_set_creation_job ADD CONSTRAINT document_set_creation_job_documentcloud_job_type_check
  CHECK (type <> 1 OR (
    source_document_set_id IS NULL
    AND contents_oid IS NULL));
ALTER TABLE document_set_creation_job ADD CONSTRAINT document_set_creation_job_csv_import_job_type_check
  CHECK (type <> 2 OR (
    contents_oid IS NOT NULL
    AND source_document_set_id IS NULL
    AND documentcloud_username IS NULL
    AND documentcloud_password IS NULL));
ALTER TABLE document_set_creation_job ADD CONSTRAINT document_set_creation_job_clone_job_type_check
  CHECK (type <> 3 OR (
    source_document_set_id IS NOT NULL
    AND contents_oid IS NULL
    AND documentcloud_username IS NULL
    AND documentcloud_password IS NULL));

ALTER TABLE document_set DROP COLUMN "type"; -- drop constraints
DROP TYPE document_set_type;

-- We won't re-build the DocumentSet constraints. Right now, the worker makes
-- assumptions about what will be in the DocumentSet, based on job types. The
-- correct solution is to put "query" and "uploaded_file_id" in the job and
-- make the worker create the DocumentSet.
--
-- Only the worker makes these assumptions. They're difficult to encode in
-- SQL. It would probably take less time to implement the correct solution.

COMMIT;

# --- !Downs

BEGIN;

DROP TABLE document_set_creation_job_type;
CREATE TYPE document_set_creation_job_type AS ENUM ('CsvImportJob', 'DocumentCloudJob', 'CloneJob'); 

LOCK TABLE document_set_creation_job;

ALTER TABLE document_set_creation_job ADD COLUMN type2 document_set_creation_job_type;
UPDATE document_set_creation_job SET type2 = CASE "type"
  WHEN 1 THEN 'DocumentCloudJob'::document_set_creation_job_type
  WHEN 2 THEN 'CsvImportJob'::document_set_creation_job_type
  WHEN 3 THEN 'CloneJob'::document_set_creation_job_type
  END;
ALTER TABLE document_set_creation_job ALTER COLUMN type2 SET NOT NULL;

ALTER TABLE document_set_creation_job DROP COLUMN "type";
ALTER TABLE document_set_creation_job RENAME COLUMN type2 TO "type";

CREATE TYPE document_set_type AS ENUM ('CsvImportDocumentSet', 'DocumentCloudDocumentSet');
ALTER TABLE document_set ADD COLUMN "type" document_set_type NOT NULL DEFAULT 'DocumentCloudDocumentSet'::document_set_type;
UPDATE document_set
  SET "type" = 'CsvImportDocumentSet'::document_set_type
  WHERE id IN (SELECT DISTINCT document_set_id FROM document WHERE "type" = 'CsvImportDocument'::document_type);

ALTER TABLE document_set ALTER COLUMN "type" DROP DEFAULT;

-- Add back old constraints.

-- 13.sql:
ALTER TABLE document_set ADD CONSTRAINT document_set_document_cloud_type_check
  CHECK (type <> 'DocumentCloudDocumentSet' OR
    (query IS NOT NULL AND uploaded_file_id IS NULL));
ALTER TABLE document_set ADD CONSTRAINT document_set_csv_import_type_check
  CHECK (type <> 'CsvImportDocumentSet' OR
    (query IS NULL AND uploaded_file_id IS NOT NULL));

-- 21.sql:
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

COMMIT;
