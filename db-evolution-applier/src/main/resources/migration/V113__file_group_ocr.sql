ALTER TABLE file_group
ADD COLUMN ocr BOOLEAN CHECK ((ocr IS NULL) = (add_to_document_set_id IS NULL));

COMMENT ON COLUMN file_group.ocr IS 'When true, find text on pages with OCR; when false, do not';
