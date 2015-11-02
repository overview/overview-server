UPDATE tree SET description = '' WHERE description IS NULL;

ALTER TABLE tree ALTER COLUMN description SET NOT NULL;
