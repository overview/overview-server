ALTER TABLE document RENAME title TO description;
ALTER TABLE document ADD title VARCHAR DEFAULT NULL;
