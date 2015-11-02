-- is_from_ocr: Describes whether a page's text came from OCR.
--
-- Also, now is the time to delete unused columns and NULLs. Wheeee! (The table
-- is under 1GB on production, so this should be okay.)

ALTER TABLE page ADD COLUMN is_from_ocr BOOLEAN;

UPDATE page SET is_from_ocr = FALSE;
UPDATE page SET data_location = '' WHERE data_location IS NULL;
UPDATE page SET text = '' WHERE text IS NULL;

ALTER TABLE page
  DROP COLUMN data_error_message,
  DROP COLUMN text_error_message,
  DROP COLUMN data,
  ALTER COLUMN is_from_ocr SET NOT NULL,
  ALTER COLUMN data_location SET NOT NULL,
  ALTER COLUMN text SET NOT NULL;
