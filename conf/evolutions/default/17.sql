# --- !Ups

ALTER TABLE document RENAME title TO description;
ALTER TABLE document ADD title VARCHAR DEFAULT NULL;


# --- !Downs

ALTER TABLE document DROP title;
ALTER TABLE document RENAME description TO title;
