# --- !Ups

ALTER TABLE document_set ADD created_at TIMESTAMP DEFAULT NULL;
UPDATE document_set SET created_at = TIMESTAMP '1970-01-01 00:00:00 GMT';
ALTER TABLE document_set ALTER COLUMN created_at SET NOT NULL;

# --- !Downs

ALTER TABLE document_set DROP created_at;
