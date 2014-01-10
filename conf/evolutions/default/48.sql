# --- !Ups

BEGIN;

ALTER TABLE document_set
  ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE document_set ALTER COLUMN deleted DROP DEFAULT;

COMMIT;


# --- !Downs

BEGIN;

ALTER TABLE document_set DROP COLUMN deleted;

COMMIT;

