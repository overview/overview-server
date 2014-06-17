# --- !Ups

BEGIN;

ALTER TABLE file ADD COLUMN view_oid OID;
UPDATE file SET view_oid = contents_oid;
ALTER TABLE file ALTER COLUMN view_oid SET NOT NULL;



COMMIT;

# --- !Downs

BEGIN;

ALTER TABLE file DROP COLUMN view_oid;

COMMIT;


