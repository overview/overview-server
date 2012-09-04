# --- !Ups

DROP INDEX user_confirmation_token;
ALTER TABLE "user" ADD CONSTRAINT unique_confirmation_token UNIQUE(confirmation_token);

DROP INDEX user_reset_password_token;
ALTER TABLE "user" ADD CONSTRAINT unique_reset_password_token UNIQUE(reset_password_token);

# --- !Downs

ALTER TABLE "user" DROP CONSTRAINT unique_reset_password_token;
CREATE INDEX user_reset_password_token ON "user" (reset_password_token);

ALTER TABLE "user" DROP CONSTRAINT unique_confirmation_token;
CREATE INDEX user_confirmation_token ON "user" (confirmation_token);
