-- Prevent deleting an API Token when a View depends on it.

CREATE INDEX view_api_token ON "view" (api_token);
ALTER TABLE "view" ADD FOREIGN KEY (api_token) REFERENCES api_token (token);
