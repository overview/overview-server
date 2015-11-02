UPDATE document SET view_url = regexp_replace(view_url, 'http://www.documentcloud.org/documents/(.*).html', '\1');
ALTER TABLE document RENAME COLUMN view_url TO documentcloud_id;
ALTER TABLE document DROP COLUMN text_url;
