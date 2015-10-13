# --- !Ups

-- is_from_ocr: Describes whether a page's text came from OCR.
--
-- Also, now is the time to delete unused columns and NULLs. Wheeee! (The table
-- is under 1GB on production, so this should be okay.)

BEGIN;

ALTER TABLE page ADD COLUMN is_from_ocr BOOLEAN;

UPDATE page SET is_from_ocr = FALSE;

ALTER TABLE page
  DROP COLUMN data_error_message,
  DROP COLUMN text_error_message,
  DROP COLUMN data,
  ALTER COLUMN is_from_ocr SET NOT NULL,
  ALTER COLUMN data_location SET NOT NULL,
  ALTER COLUMN text SET NOT NULL;

COMMIT;

# --- !Downs

ALTER TABLE page
  DROP COLUMN is_from_ocr,
  ALTER COLUMN data_location DROP NOT NULL,
  ALTER COLUMN text DROP NOT NULL,
  ADD COLUMN data BYTEA,
  ADD COLUMN data_error_message VARCHAR,
  ADD COLUMN text_error_message VARCHAR;
