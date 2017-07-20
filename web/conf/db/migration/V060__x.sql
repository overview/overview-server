UPDATE document
  SET page_number = SPLIT_PART(documentcloud_id, '#p', 2)::INT
  WHERE documentcloud_id LIKE '%#p%';
