-- This is a reversal of V041
--
-- We built something complicated to work around a simple problem: document
-- sets weren't indexed. Since then, we implemented the simple solution: index
-- the document sets.
--
-- Our code no longer needs this version number, and we can't say for sure
-- we'll need it again in the future.

ALTER TABLE document_set DROP COLUMN version;
DROP TABLE document_set_versions;
