-- Some dev-mode errors happened here. This was originally V112, but it never
-- got applied on production because of Flyway config. In upgrading it, we have
-- to account for some users having the column and other users not having it.
--
-- At this moment, nobody has any useful contents in this column. But we can't
-- DROP it because that would take forever on production, right?

DO $$
BEGIN
  BEGIN
    ALTER TABLE document ADD COLUMN pdf_notes_json_text TEXT;
  EXCEPTION
    WHEN duplicate_column THEN RAISE NOTICE 'column pdf_notes_json_text already exists';
  END;
END;
$$
