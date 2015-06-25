# --- !Ups

-- Adds a "metadata" table to documents.
--
-- Unfortunately, Postgres imposes restrictions on JSON data. We gain nothing
-- (yet) from using its JSON data types, and doing so will cause errors. So
-- let's just store it as text.
--
-- Restrictions: http://www.postgresql.org/docs/9.4/static/datatype-json.html
--
-- "document.metadata_json_text" follows a convention set in "store.json_text"
-- and "store_object.json_text".

BEGIN;

ALTER TABLE document_set ADD COLUMN metadata_schema TEXT;
UPDATE document_set SET metadata_schema = '{"version":1,"fields":[]}';
ALTER TABLE document_set ALTER COLUMN metadata_schema SET NOT NULL;

ALTER TABLE document ADD COLUMN metadata_json_text TEXT;

COMMIT;

# --- !Downs

BEGIN;

ALTER TABLE document_set DROP COLUMN metadata_schema;

ALTER TABLE document DROP COLUMN metadata_json_text;

COMMIT;
