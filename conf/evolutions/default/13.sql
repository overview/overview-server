# --- !Ups

CREATE TABLE upload (
  id                        BIGSERIAL PRIMARY KEY,
  user_id                   BIGINT NOT NULL REFERENCES "user" (id),
  guid                      UUID NOT NULL,
  last_activity             TIMESTAMP NOT NULL,
  filename                  VARCHAR(255) NOT NULL,
  bytes_uploaded            BIGINT NOT NULL,
  bytes_total               BIGINT NOT NULL,
  contents_oid              OID NOT NULL
)

# --- !Downs

SELECT lo_unlink(contents_oid) FROM upload;
DROP TABLE IF EXISTS upload CASCADE;
