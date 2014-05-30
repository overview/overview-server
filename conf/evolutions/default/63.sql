# --- !Ups

BEGIN;

ALTER TABLE page
  ADD COLUMN text VARCHAR,
  ADD COLUMN text_error_message VARCHAR,
  ALTER COLUMN data DROP NOT NULL,
  ADD COLUMN data_error_message VARCHAR;
  

DROP TABLE IF EXISTS grouped_processed_file;

COMMIT;

# --- !Downs

BEGIN;

CREATE TABLE grouped_processed_file (
  id                   BIGSERIAL PRIMARY KEY,
  file_group_id        BIGINT NOT NULL references file_group (id),
  content_type         VARCHAR NOT NULL,
  name                 VARCHAR NOT NULL,
  error_message        VARCHAR,
  text                 VARCHAR,
  size                 BIGINT NOT NULL,
  contents_oid         OID,
  page_data            BYTEA,
  page_number          INTEGER
);

CREATE INDEX grouped_processed_file_file_group_id ON grouped_processed_file (file_group_id);

ALTER TABLE page
  DROP COLUMN text,
  DROP COLUMN text_error_message,
  ALTER COLUMN data SET NOT NULL,
  DROP COLUMN data_error_message;


COMMIT;
