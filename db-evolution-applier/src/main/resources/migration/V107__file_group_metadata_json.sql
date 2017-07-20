ALTER TABLE file_group ADD COLUMN metadata_json_text TEXT;
UPDATE file_group SET metadata_json_text = '{}';
ALTER TABLE file_group ALTER COLUMN metadata_json_text SET NOT NULL;
