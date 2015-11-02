CREATE TABLE "session" (
  id UUID NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES "user" (id),
  ip INET NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);
CREATE INDEX session_user_id ON "session" (user_id);

ALTER TABLE "user"
  DROP COLUMN current_sign_in_at,
  DROP COLUMN current_sign_in_ip,
  DROP COLUMN last_sign_in_at,
  DROP COLUMN last_sign_in_ip;
