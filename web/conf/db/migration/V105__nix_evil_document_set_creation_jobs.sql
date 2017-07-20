-- We nixed some code; now we need to nix the corresponding jobs. We know they
-- don't make sense and can never be processed.

DELETE FROM document_set_creation_job
WHERE type = 4
   OR state = 4
   OR state = 5;
