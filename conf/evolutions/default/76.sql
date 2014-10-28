# --- !Ups

BEGIN;

-- The ID is a random one from uuidgen

DELETE FROM plugin WHERE url = 'about:tree';
INSERT INTO plugin (id, name, description, url) VALUES ('b8d3f14e-c017-4a9f-8913-bbcaeead9a38', 'Tree', 'Organizes documents into folders', 'about:tree');

COMMIT;

# --- !Downs

BEGIN;

DELETE FROM plugin WHERE id = 'b8d3f14e-c017-4a9f-8913-bbcaeead9a38';

COMMIT;
