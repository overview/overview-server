# --- !Ups

BEGIN;

CREATE TABLE file_group (
  id               BIGSERIAL PRIMARY KEY,
  guid             UUID NOT NULL,
  user_id          BIGINT NOT NULL REFERENCES "user" (id),
  state            INTEGER NOT NULL
);
CREATE INDEX file_group_user_id ON file_group (user_id);

CREATE TABLE file_job_state (
  id                INTEGER NOT NULL,
  description       VARCHAR NOT NULL
);

INSERT INTO file_job_state (id, description) VALUES
  (1, 'Complete'),
  (2, 'InProgress'),
  (3, 'Error');


CREATE TABLE file (
  id                BIGSERIAL PRIMARY KEY,
  guid              UUID NOT NULL,
  name              VARCHAR NOT NULL,
  content_type      VARCHAR NOT NULL,
  contents_oid      OID NOT NULL,
  size              BIGINT NOT NULL,
  uploaded_at       TIMESTAMP NOT NULL,
  state             INTEGER NOT NULL,
  text		    VARCHAR NOT NULL
);

CREATE TABLE file_group_file (
  file_group_id     BIGINT NOT NULL REFERENCES file_group (id),
  file_id           BIGINT NOT NULL REFERENCES file (id)
);
CREATE INDEX file_group_file_file_group_id ON file_group_file (file_group_id);
CREATE INDEX file_group_file_file_id ON file_group_file (file_id);

CREATE TABLE file_upload (
  id                    BIGSERIAL PRIMARY KEY,
  file_group_id         BIGINT NOT NULL REFERENCES file_group (id),
  guid                  UUID NOT NULL,
  content_disposition   VARCHAR NOT NULL,
  content_type          VARCHAR NOT NULL,
  size                  BIGINT NOT NULL,
  last_activity         TIMESTAMP NOT NULL,
  contents_oid          OID NOT NULL
);

CREATE INDEX file_upload_file_group_id ON file_upload (file_group_id);

COMMIT;



# --- !Downs

BEGIN;

DROP TABLE IF EXISTS file_upload CASCADE;
DROP TABLE IF EXISTS file_group_file CASCADE;
DROP TABLE IF EXISTS file CASCADE;
DROP TABLE IF EXISTS file_job_state CASCADE;
DROP TABLE IF EXISTS file_group CASCADE;

COMMIT;

