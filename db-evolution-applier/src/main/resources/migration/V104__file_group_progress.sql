-- Before, document_set_creation_job held FileGroup-processing progress. But
-- we want to nix that column. So now, we add some columns to file_group to
-- track progress.

ALTER TABLE file_group
  ADD COLUMN add_to_document_set_id BIGINT REFERENCES document_set (id),
  ADD COLUMN lang VARCHAR CHECK ((lang IS NULL) = (add_to_document_set_id IS NULL)),
  ADD COLUMN split_documents BOOLEAN CHECK ((split_documents IS NULL) = (add_to_document_set_id IS NULL)),
  ADD COLUMN n_files INT CHECK ((n_files IS NULL) = (add_to_document_set_id IS NULL)),
  ADD COLUMN n_bytes BIGINT CHECK ((n_files IS NULL) = (add_to_document_set_id IS NULL)),
  ADD COLUMN n_files_processed INT CHECK ((n_files_processed IS NULL) = (add_to_document_set_id IS NULL)),
  ADD COLUMN n_bytes_processed BIGINT CHECK ((n_bytes_processed IS NULL) = (add_to_document_set_id IS NULL)),
  ADD COLUMN estimated_completion_time TIMESTAMP WITH TIME ZONE,
  DROP COLUMN completed;

COMMENT ON COLUMN file_group.add_to_document_set_id IS 'When set, this file_group is an in-progress job';

ALTER TABLE document_set_creation_job DROP COLUMN file_group_id;
