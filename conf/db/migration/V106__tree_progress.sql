-- Before, trees were created from the document_set_creation_job table. That
-- led to a number of bad outcomes:
--
-- * We could not delete failed tree jobs, because we needed the error message.
-- * We needed to special-case "recluster" jobs, to ignore them when listing
--   document sets.
-- * We had tons of columns on the "document_set_creation_job" table that made
--   no sense.
--
-- Now, trees are views. This migration treats *failed* and *in-progress* trees
-- as views, too.

ALTER TABLE tree
  ALTER COLUMN root_node_id DROP NOT NULL,
  ALTER COLUMN document_count DROP NOT NULL,
  ADD COLUMN tag_id BIGINT,
  ADD COLUMN progress REAL,
  ADD COLUMN progress_description VARCHAR,
  ADD COLUMN cancelled BOOLEAN;

COMMENT ON COLUMN tree.root_node_id IS 'Root node of the fully-analyzed tree. If NULL, there is no tree from this job (but there may be an error message).';
COMMENT ON COLUMN tree.document_count IS 'Number of documents analyzed. If NULL, analysis has not been done.';
COMMENT ON COLUMN tree.cancelled IS 'A user requested to cancel analysis (to save time). The backend may ignore this hint, or it may choose to delete this tree.';

UPDATE tree
SET
  tag_id = (SELECT tag_id FROM document_set_creation_job WHERE tree.job_id = document_set_creation_job.id),
  progress = COALESCE((SELECT fraction_complete FROM document_set_creation_job WHERE tree.job_id = document_set_creation_job.id), 1.0),
  progress_description = COALESCE((SELECT status_description FROM document_set_creation_job WHERE tree.job_id = document_set_creation_job.id), ''),
  cancelled = FALSE;

-- The only NULLable columns will be tag_id, root_node_id, and document_count.
--
-- Someday we'll drop the tag_id: see
-- https://www.pivotaltracker.com/story/show/93912160
ALTER TABLE tree
  ALTER COLUMN progress SET NOT NULL,
  ALTER COLUMN progress_description SET NOT NULL,
  ALTER COLUMN cancelled SET NOT NULL,
  DROP COLUMN job_id;

DELETE FROM document_set_creation_job
WHERE type = 5;

ALTER TABLE document_set_creation_job
  DROP COLUMN supplied_stop_words,
  DROP COLUMN important_words,
  DROP COLUMN tree_title,
  DROP COLUMN tree_description,
  DROP COLUMN tag_id;

CREATE TABLE dangling_node (
  root_node_id BIGINT NOT NULL PRIMARY KEY REFERENCES node (id)
);
COMMENT ON TABLE dangling_node IS 'If Overview is not clustering, any root node mentioned in this table is garbage and its entire hierarchy should be deleted to save space';

INSERT INTO dangling_node (root_node_id) SELECT node_id FROM document_set_creation_job_node;

DROP TABLE document_set_creation_job_node;
