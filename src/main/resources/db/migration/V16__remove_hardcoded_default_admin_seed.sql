-- V16__remove_hardcoded_default_admin_seed.sql
-- C-3 (AUDIT_v5): V8__seed_default_admin.sql unconditionally created a hardcoded
-- admin@novafacts.com / Admin2024! account on every fresh database, regardless of
-- the ADMIN_EMAIL/ADMIN_PASSWORD environment variables a deployment configures.
-- AdminUserInitializer (an idempotent ApplicationRunner, already in the codebase)
-- is the correct, single, configurable bootstrap mechanism. This migration removes
-- the redundant, insecure row V8 creates so AdminUserInitializer becomes the only
-- EFFECTIVE admin-bootstrap path going forward.
--
-- V8 itself is intentionally left completely untouched. Editing or deleting an
-- already-applied migration breaks Flyway's checksum validation for every database
-- that has already run it (including this project's own dev database) — the
-- correct Flyway practice for undoing a past migration's effect is always a new,
-- forward-only migration, never a retroactive edit.
--
-- This DELETE is scoped to the EXACT hardcoded BCrypt hash V8 inserts, not just the
-- email. BCrypt hashes are randomly salted per-encode, so this can only ever match
-- the specific row V8's literal INSERT created — it cannot match a legitimately
-- (re)created admin account, even one that happens to reuse the same email and the
-- same password, since AdminUserInitializer hashes it fresh with a different salt
-- every time it runs.
--
-- Fresh database: V8 runs and creates the hardcoded row; this migration runs
-- immediately after, in the same startup, and removes it before the application
-- ever accepts a request; AdminUserInitializer then creates the one real
-- administrator from ADMIN_EMAIL/ADMIN_PASSWORD.
--
-- Existing database: if the hardcoded row is still present and untouched, it is
-- removed here too, closing the backdoor; AdminUserInitializer recreates an
-- equivalent, properly-owned account on the next startup. A database where an
-- operator already renamed/removed/changed the password on that row is unaffected,
-- since the hash will no longer match.

DELETE FROM usuario
WHERE email = 'admin@novafacts.com'
  AND password_hash = '$2b$10$M4yBMutDHHxpjiCDu6tFmeCXqplQzntLzsK5SsaizqyMIUE3oHCPi'
  AND rol_id = 1;
