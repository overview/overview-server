# --- !Ups

BEGIN;

CREATE TABLE api_token (
  token VARCHAR NOT NULL PRIMARY KEY,
  created_at TIMESTAMP NOT NULL,
  created_by VARCHAR NOT NULL,
  description VARCHAR NOT NULL,
  document_set_id BIGINT NOT NULL REFERENCES document_set (id)
);

CREATE INDEX api_token_created_by ON api_token (created_by);
CREATE INDEX api_token_document_set_id ON api_token (document_set_id);

COMMIT;

# --- !Downs

BEGIN;

DROP TABLE api_token;

COMMIT;
