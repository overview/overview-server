-- The original file2 design had a vague distinction between "pipelineOptions"
-- and, er, everything else. Should "contentType" be within pipelineOptions,
-- for instance? And we never had the conversation about whether
-- pipelineOptions should be schemaless. Right now, before launch (and the
-- creation of hundreds of millions of files), is the right time to simplify.
--
-- We're nixing "pipelineOptions.stepsRemaining," too. [adam, 2018-03-20] It
-- seems useful in my mind, but at this stage it just adds complexity without
-- giving anything in return.
ALTER TABLE file2
ADD COLUMN want_ocr BOOLEAN,
ADD COLUMN want_split_by_page BOOLEAN
;

UPDATE file2
SET
  want_ocr = (CONVERT_FROM(pipeline_options_json_utf8, 'utf-8')::JSON ->> 'ocr')::BOOLEAN,
  want_split_by_page = (CONVERT_FROM(pipeline_options_json_utf8, 'utf-8')::JSON ->> 'splitByPage')::BOOLEAN
  ;

ALTER TABLE file2
ALTER COLUMN want_ocr SET NOT NULL,
ALTER COLUMN want_split_by_page SET NOT NULL,
DROP COLUMN pipeline_options_json_utf8
;
