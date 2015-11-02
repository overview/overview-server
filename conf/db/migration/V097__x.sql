CREATE TYPE document_display_method AS ENUM ('auto', 'pdf', 'page', 'text', 'html');

ALTER TABLE document ADD COLUMN display_method document_display_method;
