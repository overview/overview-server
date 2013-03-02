# --- !Ups

ALTER TABLE document_set ADD COLUMN document_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE document_set ALTER COLUMN document_count DROP DEFAULT;

# --- !Downs

ALTER TABLE document_set DROP COLUMN document_count;
