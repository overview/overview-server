# --- !Ups

BEGIN;

-- name_to_color() is a clone of the client-side implementation at the time
-- this evolution was created. Don't bother changing it if the client-side
-- implementation changes, because it won't be called again after this evolution
-- is run. That's the whole point of this evolution.
--
-- Oh, and we cannot use $$ to delimit the function or insert [semicolon] in
-- functions, because of
-- http://play.lighthouseapp.com/projects/82401/tickets/212
-- and https://github.com/playframework/Play20/pull/134. That means we need
-- some funky syntax.
--
-- We cannot even include [semicolon] in this comment, as it will break Play.
--
-- [semicolon] is \x3b.
CREATE FUNCTION pg_temp.name_to_color(str varchar) RETURNS char(6) AS E'
  DECLARE i integer := 0\x3b
  DECLARE h bigint := 0\x3b
  DECLARE idx integer\x3b
  BEGIN
    FOR i in 1..LENGTH(str) LOOP
      h = (h * 31 + ASCII(SUBSTRING(str, i, 1))) & 4294967295\x3b
    END LOOP\x3b
    SELECT ((cast(cast(h as bit(32)) as int4) % 23) + 23) % 23 INTO idx\x3b
    RETURN CASE idx
      WHEN 0 THEN ''ff0009''
      WHEN 1 THEN ''ff7700''
      WHEN 2 THEN ''fff700''
      WHEN 3 THEN ''09ff00''
      WHEN 4 THEN ''00ff77''
      WHEN 5 THEN ''00fff7''
      WHEN 6 THEN ''0089ff''
      WHEN 7 THEN ''0009ff''
      WHEN 8 THEN ''7700ff''
      WHEN 9 THEN ''f700ff''
      WHEN 10 THEN ''ff0089''
      WHEN 11 THEN ''ff7378''
      WHEN 12 THEN ''ffb573''
      WHEN 13 THEN ''fffb73''
      WHEN 14 THEN ''beff73''
      WHEN 15 THEN ''78ff73''
      WHEN 16 THEN ''73ffb5''
      WHEN 17 THEN ''73fffb''
      WHEN 18 THEN ''73beff''
      WHEN 19 THEN ''7378ff''
      WHEN 20 THEN ''b573ff''
      WHEN 21 THEN ''fb73ff''
      ELSE ''ff73be''
    END\x3b
  END\x3b
' LANGUAGE plpgsql;

UPDATE tag SET color = pg_temp.name_to_color(name) WHERE color IS NULL;
DROP FUNCTION pg_temp.name_to_color(varchar);
ALTER TABLE tag ALTER COLUMN color SET NOT NULL;

COMMIT;

# --- !Downs

ALTER TABLE tag ALTER COLUMN color DROP NOT NULL;
