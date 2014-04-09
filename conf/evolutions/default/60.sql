# --- !Ups

BEGIN;

UPDATE document
  SET page_number = SPLIT_PART(documentcloud_id, '#p', 2)::INT
  WHERE documentcloud_id LIKE '%#p%';

COMMIT;

# --- !Downs

BEGIN;

UPDATE document
  SET page_number = NULL
  WHERE documentcloud_id LIKE '%#p%';

COMMIT;
