# --- !Ups

ALTER TABLE document_set ADD COLUMN import_overflow_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE document_set ALTER COLUMN import_overflow_count DROP DEFAULT;

# --- !Downs

ALTER TABLE document_set DROP COLUMN import_overflow_count;


