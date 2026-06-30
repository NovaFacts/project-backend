-- V8__seed_default_admin.sql
-- Seeds one default Administrador user if no admin exists.
-- This migration runs exactly ONCE on a fresh database.
-- On existing databases where any user with rol_id=1 already exists, it is a no-op.
--
-- Default credentials:
--   email    : admin@novafacts.com
--   password : Admin2024!
--   hash     : BCrypt $2b$10, 10 rounds — verified compatible with Spring Security BCryptPasswordEncoder
--
-- SECURITY: This hash is committed to version control intentionally as a bootstrap credential.
-- Change the password immediately after the first login in any non-development environment.
-- To customize the bootstrap email before first deployment, edit this file before running
-- the container for the first time (Flyway checksums prevent re-running it afterwards).

INSERT INTO usuario (email, password_hash, nombre, rol_id, activo, creado_en)
SELECT
    'admin@novafacts.com',
    '$2b$10$M4yBMutDHHxpjiCDu6tFmeCXqplQzntLzsK5SsaizqyMIUE3oHCPi',
    'Administrador Sistema',
    1,
    true,
    now()
WHERE NOT EXISTS (
    SELECT 1 FROM usuario WHERE rol_id = 1
);
