-- Temporarily hide the cancel button when users try adding files.
--
-- At the time this evolution was written, we had no means to "undo" a partial
-- import: that is, to delete the documents that have already been created.
-- This leaves document sets in an inconsistent state. Solution: nix the
-- feature.

ALTER TABLE document_set_creation_job ADD COLUMN can_be_cancelled BOOLEAN DEFAULT TRUE;
