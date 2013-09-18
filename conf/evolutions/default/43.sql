# --- !Ups

BEGIN;

ALTER TABLE document DROP COLUMN "type";
DROP TYPE document_type;

COMMIT;



# --- !Downs

BEGIN;

CREATE TYPE document_type AS ENUM ('DocumentCloudDocument', 'CsvImportDocument');

ALTER TABLE document ADD COLUMN "type" document_type;
UPDATE document SET "type" = CASE  WHEN documentcloud_id IS NULL THEN 'CsvImportDocument'::document_type
  ELSE 'DocumentCloudDocument'::document_type
  END;

ALTER TABLE document ADD CONSTRAINT document_document_cloud_type_check
  CHECK (type <> 'DocumentCloudDocument' OR
    (documentcloud_id IS NOT NULL
      AND url IS NULL
      AND supplied_id IS NULL));
ALTER TABLE document ADD CONSTRAINT document_csv_import_type_check
  CHECK (type <> 'CsvImportDocument' OR
    (documentcloud_id IS NULL
      AND text IS NOT NULL));


COMMIT;

