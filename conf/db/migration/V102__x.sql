-- Describes whether a document's text came from OCR.
--
-- We don't set this column to NOT NULL, even though it should be. Setting
-- columns to NOT NULL forces a full table scan (not to mention, a rewrite of
-- all rows), and without a big plan that causes downtime on production. We
-- need a big plan to clean up the document table anyway. In the in the
-- meantime, one extra row won't make a difference.

ALTER TABLE document ADD is_from_ocr BOOLEAN;
