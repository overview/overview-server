# --- !Ups

BEGIN;

ALTER TABLE node DROP COLUMN cached_document_ids;

COMMIT;

# --- !Downs

BEGIN;

ALTER TABLE node ADD COLUMN cached_document_ids BIGINT[];

COMMIT;


