# --- !Ups

BEGIN;

ALTER TABLE document ADD COLUMN type2 INT;

UPDATE document SET type2 = CASE "type"
  WHEN 'DocumentCloudDocument'::document_type THEN 1
  WHEN 'CsvImportDocument'::document_type THEN 2
  END;

ALTER TABLE document ALTER COLUMN type2 SET NOT NULL;

ALTER TABLE document DROP COLUMN "type";
ALTER TABLE document RENAME COLUMN type2 TO "type";

DROP TYPE document_type;

CREATE TABLE document_type (
  id      INT NOT NULL PRIMARY KEY,
  name    VARCHAR NOT NULL
);

INSERT INTO document_type (id, name) VALUES
  (1, 'DocumentCloudDocument'),
  (2, 'CsvImportDocument'),
  (3, 'FileUploadDocument');

ALTER TABLE document ADD CONSTRAINT document_document_cloud_type_check
  CHECK (type <> 1 OR
    (documentcloud_id IS NOT NULL
      AND url IS NULL
      AND supplied_id IS NULL));
ALTER TABLE document ADD CONSTRAINT document_csv_import_type_check
  CHECK (type <> 2 OR
    (documentcloud_id IS NULL
      AND text IS NOT NULL));
ALTER TABLE document ADD CONSTRAINT document_file_upload_type_check
  CHECK (type <> 3 OR
    (documentcloud_id IS NULL
      AND url IS NULL 
      AND supplied_id IS NULL));


COMMIT;



# --- !Downs

BEGIN;

DROP TABLE document_type;
CREATE TYPE document_type AS ENUM ('DocumentCloudDocument', 'CsvImportDocument');

ALTE TABLE document ADD COLUMN type2 document_type;
UPDATE document SET type2 = CASE "type"
  WHEN 1 THEN 'DocumentCloudDocument'::document_type
  WHEN 2 THEN 'CsvImportJob'::document_type
  END;

DELETE FROM document WHERE "type" = 3;
ALTER TABLE document ALTER COLUMN type2 SET NOT NULL;
ALTER TABLE document DROP COLUMN "type";
ALTER TABLE document RENAME COLUMN type2 TO "type";

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

