# --- !Ups


BEGIN;


ALTER TABLE document_set_creation_job
  ALTER COLUMN important_words DROP DEFAULT;

ALTER TABLE document_set
  ALTER COLUMN important_words DROP DEFAULT;

COMMIT;

# --- !Downs


BEGIN;

ALTER TABLE document_set
  ALTER COLUMN important_words SET DEFAULT '';

ALTER TABLE document_set_creation_job
  ALTER COLUMN important_words SET DEFAULT '';


COMMIT;

