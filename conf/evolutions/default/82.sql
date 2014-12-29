# --- !Ups

BEGIN;

-- We used to have a "state" column to enumerate two states. That implied a
-- state-machine implementation we neither need nor have.
--
-- When a file_group is first created, completed=false. Once the user indicates
-- he/she is finished uploading files, completed=true. That's it.

ALTER TABLE file_group ADD COLUMN completed BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE file_group SET completed = FALSE WHERE state <> 1;
ALTER TABLE file_group ALTER COLUMN completed DROP DEFAULT;
ALTER TABLE file_group DROP COLUMN state;
DROP TABLE file_job_state;

-- A user can have multiple file groups: one per API token.
--
-- No foreign keys: if the user deletes the API token (or if we delete a user),
-- we'll clean things up asynchronously.
DROP INDEX file_group_user_email;
ALTER TABLE file_group ADD COLUMN api_token VARCHAR;
CREATE INDEX file_group_user_email_api_token ON file_group (user_email, api_token);

COMMIT;

# --- !Downs

BEGIN;

DROP INDEX file_group_user_email_api_token;
DELETE FROM file_group WHERE api_token IS NOT NULL;
ALTER TABLE file_group DROP COLUMN api_token;
CREATE INDEX file_group_user_email ON file_group (user_email);

ALTER TABLE file_group ADD COLUMN state INT NOT NULL DEFAULT 1;
UPDATE file_group SET state = 2 WHERE completed = FALSE;
ALTER TABLE file_group ALTER COLUMN state DROP DEFAULT;
ALTER TABLE file_group DROP COLUMN completed;

CREATE TABLE file_job_state (
  id INT NOT NULL,
  description VARCHAR NOT NULL
);
INSERT INTO file_job_state (id, description) VALUES
  (1, 'Complete'),
  (2, 'InProgress');

COMMIT;
