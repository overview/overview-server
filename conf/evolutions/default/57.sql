# --- !Ups

BEGIN;

UPDATE "user"
SET
  email = 'admin@overviewproject.org',
  password_hash = '$2a$07$uvfQFB0x3V3/IUZb0VF5nuj3ZIX0.yuDH6F635q/JU1MlrThPlvy.'
WHERE email = 'admin@overview-project.org'
  AND password_hash = '$2a$07$ZNI3MdA1MK7Td2w1EKpl5u38nll/MvlaRfZn0S8HLerNuP2hoD5JW';

COMMIT;

# --- !Downs

BEGIN;

UPDATE "user"
SET
  email = 'admin@overview-project.org',
  password_hash = '$2a$07$ZNI3MdA1MK7Td2w1EKpl5u38nll/MvlaRfZn0S8HLerNuP2hoD5JW'
WHERE email = 'admin@overviewproject.org',
  AND password_hash = '$2a$07$uvfQFB0x3V3/IUZb0VF5nuj3ZIX0.yuDH6F635q/JU1MlrThPlvy.';

COMMIT;
