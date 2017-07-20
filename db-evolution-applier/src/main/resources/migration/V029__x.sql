-- Change document_set_user.role from an ENUM to an INT, because Squeryl isn't
-- configurable in the way we need it to be.

-- No, code doesn't use the ownership table. It's just for documentation.
CREATE TABLE ownership (id INT NOT NULL PRIMARY KEY, name VARCHAR NOT NULL);
INSERT INTO ownership (id, name) VALUES (1, 'Owner');
INSERT INTO ownership (id, name) VALUES (2, 'Viewer');

ALTER TABLE document_set_user ADD COLUMN role2 INT NOT NULL DEFAULT 1;
UPDATE document_set_user SET role2 = 2 WHERE role = 'Viewer'::document_set_user_role_type;
ALTER TABLE document_set_user ALTER COLUMN role2 DROP DEFAULT;

ALTER TABLE document_set_user DROP COLUMN role;
ALTER TABLE document_set_user RENAME COLUMN role2 TO role;
ALTER TABLE document_set_user ADD CONSTRAINT document_set_user_role_check CHECK (role IN (1, 2));

DROP TYPE document_set_user_role_type;
