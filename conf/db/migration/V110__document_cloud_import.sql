CREATE TABLE document_cloud_import (
  id SERIAL PRIMARY KEY,
  document_set_id BIGINT NOT NULL REFERENCES document_set(id),
  query VARCHAR NOT NULL,
  username VARCHAR NOT NULL,
  password VARCHAR NOT NULL,
  split_pages BOOLEAN NOT NULL,
  lang CHAR(2) NOT NULL,
  n_id_lists_fetched INT NOT NULL,
  n_id_lists_total INT,
  n_fetched INT NOT NULL,
  n_total INT,
  cancelled BOOLEAN NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ON document_cloud_import (document_set_id);
COMMENT ON COLUMN document_cloud_import.n_total IS 'When NOT NULL, we have received one response from DocumentCloud';
COMMENT ON COLUMN document_cloud_import.n_id_lists_fetched IS 'When ceil(n_total/1000), we have fetched all id_lists';

CREATE TABLE document_cloud_import_id_list (
  id SERIAL PRIMARY KEY,
  document_cloud_import_id INT NOT NULL REFERENCES document_cloud_import (id),
  page_number INT NOT NULL,
  ids_string VARCHAR NOT NULL,
  n_documents INT NOT NULL,
  n_pages INT NOT NULL
);
CREATE INDEX ON document_cloud_import_id_list (document_cloud_import_id);
 
DROP TABLE document_set_creation_job;
DROP TABLE document_set_creation_job_type;
