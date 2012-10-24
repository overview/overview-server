# --- !Ups

ALTER TABLE tag DROP CONSTRAINT tag_document_set_id_name_key;

# --- !Downs

ALTER TABLE tag ADD UNIQUE (document_set_id, name);
