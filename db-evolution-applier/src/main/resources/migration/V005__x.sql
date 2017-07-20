ALTER TABLE log_entry DROP username;
ALTER TABLE log_entry ADD user_id BIGINT REFERENCES "user" (id);
UPDATE log_entry SET user_id = (SELECT id FROM "user" LIMIT 1);
ALTER TABLE log_entry ALTER COLUMN user_id SET NOT NULL;
CREATE INDEX log_entry_user_id ON log_entry (user_id);
