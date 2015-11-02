LOCK TABLE document;

ALTER TABLE document DROP COLUMN "type";
DROP TYPE document_type;
