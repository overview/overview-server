# --- !Ups

ALTER TABLE "user" ADD COLUMN email_subscriber BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE "user" ALTER COLUMN email_subscriber DROP DEFAULT;


# --- !Downs

ALTER TABLE "user" DROP COLUMN email_subscriber;
