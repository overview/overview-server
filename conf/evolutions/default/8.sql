# --- !Ups

UPDATE document SET view_url = regexp_replace(view_url, 'http://www.documentcloud.org/documents/(.*).html', '\1');
ALTER TABLE document RENAME COLUMN view_url TO documentcloud_id;
ALTER TABLE document DROP COLUMN text_url;

# --- !Downs

ALTER TABLE document ADD COLUMN text_url VARCHAR(511);
UPDATE document SET text_url = regexp_replace(documentcloud_id, '([^-]*)-(.*).html', 'http://s3.documentcloud.org/documents/\1/\2.txt');
ALTER TABLE document ALTER COLUMN text_url SET NOT NULL;
ALTER TABLE document RENAME COLUMN documentcloud_id TO view_url;
UPDATE document SET view_url = regexp_replace(view_url, '(.*)', 'http://www.documentcloud.org/documents/\1.html');

