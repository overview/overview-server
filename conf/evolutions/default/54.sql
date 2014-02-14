# --- !Ups


BEGIN;

ALTER TABLE document_set_creation_job ADD COLUMN tree_title VARCHAR(511);

UPDATE document_set_creation_job SET tree_title = document_set.title
  FROM document_set
    WHERE document_set_creation_job.type = 5 
      AND  document_set_creation_job.document_set_id = document_set_id ;

ALTER TABLE document_set_creation_job ADD CONSTRAINT document_set_creation_job_reclustering_job_type_check
  CHECK (type <> 5 OR tree_title IS NOT NULL);

COMMIT;

# --- !Downs


BEGIN;

ALTER TABLE document_set_creation_job DROP CONSTRAINT document_set_creation_job_reclustering_job_type_check;
ALTER TABLE document_set_creation_job DROP COLUMN tree_title;

COMMIT;

