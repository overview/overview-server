# --- !Ups

ALTER TABLE document_set_creation_job DROP CONSTRAINT document_set_creation_job_state_check;
ALTER TABLE document_set_creation_job ADD CONSTRAINT document_set_creation_job_state_check CHECK (state IN (0,1,2,3));

# --- !Downs


DELETE FROM log_entry USING document_set_creation_job
  WHERE document_set_creation_job.state = 3 AND
        document_set_creation_job.document_set_id = log_entry.document_set_id;

DELETE FROM document_tag USING document_set_creation_job, document
  WHERE document_set_creation_job.state = 3 AND
        document_set_creation_job.document_set_id = document.document_set_id AND
        document_tag.document_id = document.id;

DELETE FROM tag USING document_set_creation_job
  WHERE document_set_creation_job.state = 3 AND
        document_set_creation_job.document_set_id = tag.document_set_id;

DELETE FROM node_document USING document_set_creation_job, document                                                                        WHERE document_set_creation_job.state = 3 AND
        document_set_creation_job.document_set_id = document.document_set_id AND  
        node_document.document_id = document.id;

DELETE FROM node USING document_set_creation_job
  WHERE document_set_creation_job.state = 3 AND
        document_set_creation_job.document_set_id = node.document_set_id;

DELETE FROM document USING document_set_creation_job
  WHERE document_set_creation_job.state = 3 AND
        document_set_creation_job.document_set_id = document.document_set_id;

DELETE FROM document_set_user USING document_set_creation_job
  WHERE document_set_creation_job.state = 3 AND
        document_set_creation_job.document_set_id = document_set_user.document_set_id;

DELETE FROM document_set_creation_job WHERE document_set_creation_job.state = 3;


ALTER TABLE document_set_creation_job DROP CONSTRAINT document_set_creation_job_state_check;
ALTER TABLE document_set_creation_job ADD CONSTRAINT document_set_creation_job_state_check CHECK (state IN (0,1,2));

