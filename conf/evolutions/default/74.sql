# --- !Ups

BEGIN;

CREATE TABLE plugin (
  id UUID NOT NULL PRIMARY KEY,
  name VARCHAR NOT NULL,
  description VARCHAR NOT NULL,
  url VARCHAR NOT NULL
);

COMMIT;

# --- !Downs

BEGIN;

DROP TABLE plugin;

COMMIT;
