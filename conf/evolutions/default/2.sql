# --- !Ups

CREATE TABLE "user" (
  id                        BIGSERIAL PRIMARY KEY,
  email                     VARCHAR(255) NOT NULL UNIQUE,
  role                      INT NOT NULL DEFAULT 1 CHECK (role IN (1, 2)),
  password_hash             CHAR(60) NOT NULL,
  confirmation_token        VARCHAR(60) DEFAULT NULL,
  confirmation_sent_at      TIMESTAMP DEFAULT NULL,
  confirmed_at              TIMESTAMP DEFAULT NULL,
  reset_password_token      VARCHAR(60) DEFAULT NULL,
  reset_password_sent_at    TIMESTAMP DEFAULT NULL,
  current_sign_in_at        TIMESTAMP DEFAULT NULL,
  current_sign_in_ip        VARCHAR(45) DEFAULT NULL,
  last_sign_in_at           TIMESTAMP DEFAULT NULL,
  last_sign_in_ip           VARCHAR(45) DEFAULT NULL
);
CREATE INDEX user_confirmation_token ON "user" (confirmation_token);
CREATE INDEX user_reset_password_token ON "user" (reset_password_token);

CREATE TABLE document_set_user (
  document_set_id           BIGINT NOT NULL REFERENCES document_set (id),
  user_id                   BIGINT NOT NULL REFERENCES "user" (id),
  PRIMARY KEY (document_set_id, user_id)
);
CREATE INDEX document_set_user_document_set_id ON document_set_user (document_set_id);
CREATE INDEX document_set_user_user_id ON document_set_user (user_id);

INSERT INTO "user" (email, role, password_hash, confirmed_at)
VALUES ('admin@overview-project.org', 2, '$2a$07$ZNI3MdA1MK7Td2w1EKpl5u38nll/MvlaRfZn0S8HLerNuP2hoD5JW', TIMESTAMP '1970-01-01 00:00:00');

INSERT INTO document_set_user (document_set_id, user_id)
SELECT ds.id, u.id FROM document_set ds, "user" u;

# --- !Downs

DROP TABLE IF EXISTS document_set_user CASCADE;
DROP TABLE IF EXISTS "user" CASCADE;
