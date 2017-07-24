-- https://www.postgresql.org/docs/current/static/sql-altertable.html#AEN75832
-- says "The DROP COLUMN form does not physically remove the column, but simply
-- makes it invisible to SQL operations." That's good, because it's fast.
ALTER TABLE document DROP COLUMN description;
