ALTER TABLE file ADD COLUMN view_oid OID;
UPDATE file SET view_oid = contents_oid;
ALTER TABLE file ALTER COLUMN view_oid SET NOT NULL;
