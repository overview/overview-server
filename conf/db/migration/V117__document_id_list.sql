CREATE TABLE document_id_list (
  id BIGSERIAL PRIMARY KEY,
  document_set_id INT NOT NULL REFERENCES document_set (id),
  field_name VARCHAR NOT NULL,
  document_32bit_ids INT[] NOT NULL
);

COMMENT ON COLUMN document_id_list.field_name IS 'The field this list was sorted by';
COMMENT ON COLUMN document_id_list.document_32bit_ids IS 'The lower 32 bits of the document IDs. We assume the upper 32 bits are document_set_id.';

CREATE UNIQUE INDEX ON document_id_list (document_set_id, field_name);
