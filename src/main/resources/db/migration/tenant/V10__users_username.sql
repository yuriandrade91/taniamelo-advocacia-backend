-- V10 (tenant): username para login por email OU username.
-- username é único dentro do schema do tenant (dois escritórios podem ter o
-- mesmo username sem conflito, pois vivem em schemas distintos). Backfill dos
-- usuários existentes a partir da parte local do e-mail.
ALTER TABLE users ADD COLUMN username VARCHAR(100);

UPDATE users SET username = split_part(email, '@', 1) WHERE username IS NULL;

ALTER TABLE users ALTER COLUMN username SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT ux_users_username UNIQUE (username);
