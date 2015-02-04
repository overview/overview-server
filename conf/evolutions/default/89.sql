# --- !Ups

BEGIN;

DROP TABLE log_entry;

COMMIT;

# --- !Downs

BEGIN;

CREATE TABLE log_entry (
   id BIGSERIAL NOT NULL PRIMARY KEY,
   document_set_id BIGINT NOT NULL REFERENCES document_set (id),
   "date" TIMESTAMP WITHOUT TIME ZONE NOT NULL,
   component VARCHAR(255) NOT NULL,
   action VARCHAR(255) NOT NULL,
   details VARCHAR(255) NOT NULL,
   user_id bigint NOT NULL REFERENCES "user" (id)
);

CREATE INDEX log_entry_document_set_id ON log_entry (document_set_id);
CREATE INDEX log_entry_user_id ON log_entry (user_id);

COMMIT;
