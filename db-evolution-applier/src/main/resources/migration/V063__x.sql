ALTER TABLE page
  ADD COLUMN text VARCHAR,
  ADD COLUMN text_error_message VARCHAR,
  ALTER COLUMN data DROP NOT NULL,
  ADD COLUMN data_error_message VARCHAR;
  

DROP TABLE IF EXISTS grouped_processed_file;
