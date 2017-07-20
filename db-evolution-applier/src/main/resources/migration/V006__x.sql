DROP INDEX user_confirmation_token;
ALTER TABLE "user" ADD CONSTRAINT unique_confirmation_token UNIQUE(confirmation_token);

DROP INDEX user_reset_password_token;
ALTER TABLE "user" ADD CONSTRAINT unique_reset_password_token UNIQUE(reset_password_token);
