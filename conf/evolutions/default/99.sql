# --- !Ups

BEGIN;

UPDATE "user"
SET
  email = 'admin@overviewdocs.com',
  password_hash = '$2a$07$Utyu474eUB7itxnUCT3SQeNYDyIteIHce6pqkZsLZdztCPOfVK3kC'
WHERE email = 'admin@overviewproject.org'
  AND password_hash = '$2a$07$uvfQFB0x3V3/IUZb0VF5nuj3ZIX0.yuDH6F635q/JU1MlrThPlvy.';

COMMIT;

# --- !Downs

BEGIN;

UPDATE "user"
SET
  email = 'admin@overviewproject.org',
  password_hash = '$2a$07$uvfQFB0x3V3/IUZb0VF5nuj3ZIX0.yuDH6F635q/JU1MlrThPlvy.'
WHERE email = 'admin@overviewdocs.com'
  AND password_hash = '$2a$07$Utyu474eUB7itxnUCT3SQeNYDyIteIHce6pqkZsLZdztCPOfVK3kC';

COMMIT;
