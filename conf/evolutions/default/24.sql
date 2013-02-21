# --- !Ups

ALTER TABLE document_set_user ADD COLUMN user_email VARCHAR(255);
UPDATE document_set_user
  SET user_email = "user".email
  FROM "user"
  WHERE document_set_user.user_id = "user".id;
ALTER TABLE document_set_user ALTER COLUMN user_email SET NOT NULL;
CREATE INDEX document_set_user_user_email ON document_set_user (user_email);

ALTER TABLE document_set_user DROP COLUMN user_id;
ALTER TABLE document_set_user ADD PRIMARY KEY (document_set_id, user_email);

CREATE TYPE document_set_user_role_type AS ENUM ('Owner', 'Viewer');
ALTER TABLE document_set_user ADD COLUMN role document_set_user_role_type NOT NULL DEFAULT 'Owner'::document_set_user_role_type;
ALTER TABLE document_set_user ALTER COLUMN role DROP DEFAULT;


# --- !Downs

ALTER TABLE document_set_user DROP COLUMN role;
DROP TYPE document_set_user_role_type;

ALTER TABLE document_set_user ADD COLUMN user_id BIGINT REFERENCES "user" (id);
UPDATE document_set_user
  SET user_id = "user".id
  FROM "user"
  WHERE document_set_user.user_email = "user".email;

ALTER TABLE document_set_user ALTER COLUMN user_id SET NOT NULL;
CREATE INDEX document_set_user_user_id ON document_set_user (user_id);

ALTER TABLE document_set_user DROP COLUMN user_email;
ALTER TABLE document_set_user ADD PRIMARY KEY (document_set_id, user_id);


