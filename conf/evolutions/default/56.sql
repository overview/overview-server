# --- !Ups

ALTER TABLE tree ADD COLUMN description VARCHAR DEFAULT '';
ALTER TABLE tree ALTER COLUMN description DROP DEFAULT;

# --- !Downs

ALTER TABLE tree DROP COLUMN description;

