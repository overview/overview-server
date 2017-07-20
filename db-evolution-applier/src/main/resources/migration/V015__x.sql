ALTER TABLE node 
  ADD cached_document_ids BIGINT[],
  ADD cached_size INT;

UPDATE node SET cached_document_ids = ( 
  SELECT ARRAY_AGG(id) FROM ( 
    SELECT id FROM document
    INNER JOIN node_document ON
     node_document.node_id = node.id AND 
     node_document.document_id = document.id
    ORDER BY NULLIF(document.title, '') 
    LIMIT 10) sorted_docs);

UPDATE node SET cached_size = (
  SELECT COUNT(*) FROM node_document WHERE node.id = node_id);
