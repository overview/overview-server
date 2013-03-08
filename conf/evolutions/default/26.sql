# --- !Ups

ALTER TABLE document ALTER COLUMN id DROP DEFAULT;
DROP SEQUENCE document_id_seq;

ALTER TABLE node ALTER COLUMN id DROP DEFAULT;
DROP SEQUENCE node_id_seq;

# --- !Downs

CREATE SEQUENCE node_id_seq;
SELECT SETVAL('node_id_seq', MAX(id) + 1) FROM node;
ALTER TABLE node ALTER COLUMN id SET DEFAULT nextval('node_id_seq'::regclass);

CREATE SEQUENCE document_id_seq;
SELECT SETVAL('document_id_seq', MAX(id) + 1) FROM document;
ALTER TABLE document ALTER COLUMN id SET DEFAULT nextval('document_id_seq'::regclass);

