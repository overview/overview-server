# --- !Ups

BEGIN;

CREATE TYPE document_display_method AS ENUM ('auto', 'pdf', 'page', 'text', 'html');

ALTER TABLE document ADD COLUMN display_method document_display_method;


COMMIT;

# --- !Downs

BEGIN;

ALTER TABLE document DROP COLUMN display_method;
DROP TYPE document_display_method;


COMMIT;


