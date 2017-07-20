LOCK TABLE page;
UPDATE page SET data_size = LENGTH(data) WHERE data_size IS NULL;
ALTER TABLE page ALTER COLUMN data_size SET NOT NULL;
