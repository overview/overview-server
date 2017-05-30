-- As of 2017-05-29, grouped_file_upload is <70MB and has ~250k rows.

ALTER TABLE grouped_file_upload ADD document_metadata_json_text TEXT;

-- Usually, we like a NOT NULL ... but not here. NULL means the user did not
-- set the metadata during upload for this file. Anything else means the user
-- _did_ set the metadata.
