# --- !Ups

BEGIN;

ALTER TABLE search_result ADD created_at TIMESTAMP DEFAULT NULL;
UPDATE search_result SET created_at = TIMESTAMP '1970-01-01 00:00:00 GMT';
ALTER TABLE search_result ALTER COLUMN created_at SET NOT NULL;

COMMIT;

# --- !Downs

ALTER TABLE search_result DROP created_at;

