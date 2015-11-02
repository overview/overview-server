-- Add "store" top-level object, which takes over for viz.json
-- Rename "viz" to "view"

CREATE TABLE store (
  id BIGSERIAL PRIMARY KEY,
  api_token VARCHAR NOT NULL REFERENCES api_token (token),
  json_text VARCHAR NOT NULL
);

-- Use existing viz IDs as store IDs, for easy conversion
INSERT INTO store (id, api_token, json_text) SELECT id, api_token, json_text FROM viz;
SELECT SETVAL('store_id_seq', (SELECT MAX(id) FROM store));

CREATE INDEX store_api_token ON store (api_token);

ALTER TABLE viz DROP COLUMN json_text;

-- Begin the mass renaming. We drop and create foreign keys to rename them.

ALTER TABLE document_viz_object DROP CONSTRAINT document_viz_object_viz_object_id_fkey;

ALTER TABLE viz_object DROP CONSTRAINT viz_object_viz_id_fkey;
DROP INDEX viz_object_viz_id;
DROP INDEX viz_object_viz_id_indexed_long;
DROP INDEX viz_object_viz_id_indexed_string;
ALTER TABLE viz_object RENAME viz_id TO store_id;
ALTER TABLE viz_object RENAME TO store_object;
ALTER INDEX viz_object_pkey RENAME TO store_object_pkey;
CREATE INDEX store_object_store_id ON store_object (store_id);
CREATE INDEX store_object_store_id_indexed_long ON store_object (store_id, indexed_long) WHERE indexed_long IS NOT NULL;
CREATE INDEX store_object_store_id_indexed_string ON store_object (store_id, indexed_string) WHERE indexed_string IS NOT NULL;
ALTER TABLE store_object ADD FOREIGN KEY (store_id) REFERENCES store (id);

CREATE SEQUENCE store_object_id_seq;
ALTER TABLE store_object ALTER COLUMN id SET DEFAULT nextval('store_object_id_seq');
ALTER SEQUENCE store_object_id_seq OWNED BY store_object.id;
SELECT SETVAL('store_object_id_seq', (SELECT MAX(id) FROM store_object));

ALTER TABLE document_viz_object DROP CONSTRAINT document_viz_object_document_id_fkey;
DROP INDEX document_viz_object_viz_object_id_document_id;
ALTER TABLE document_viz_object RENAME viz_object_id TO store_object_id;
ALTER TABLE document_viz_object RENAME TO document_store_object;
ALTER INDEX document_viz_object_pkey RENAME TO document_store_object_pkey;
CREATE INDEX document_store_object_store_object_id_document_id ON document_store_object (store_object_id, document_id);
ALTER TABLE document_store_object ADD FOREIGN KEY (document_id) REFERENCES document (id);
ALTER TABLE document_store_object ADD FOREIGN KEY (store_object_id) REFERENCES store_object (id);

ALTER TABLE viz DROP CONSTRAINT viz_document_set_id_fkey;
ALTER TABLE viz RENAME TO "view";
ALTER INDEX viz_pkey RENAME TO view_pkey;
ALTER INDEX viz_document_set_id RENAME TO view_document_set_id;
ALTER TABLE "view" ADD FOREIGN KEY (document_set_id) REFERENCES document_set (id);
