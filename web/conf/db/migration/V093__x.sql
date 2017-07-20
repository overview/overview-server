-- Create a new type of API token: a "create document set" API token.
--
-- Eventually, the API-token data model will need to be more complex, perhaps
-- with a bit field of permissions. But for now, that would be overkill. A
-- plugin today has one of two permissions:
--
-- A. read/write a document set (when document_set_id IS NOT NULL)
-- B. create document sets (when document_set_id IS NULL)
--
-- When we give API tokens more permissions, we'll want another evolution.

ALTER TABLE api_token
ALTER COLUMN document_set_id
DROP NOT NULL;
