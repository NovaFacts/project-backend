-- V10__uppercase_anticipo_devolucion_estados.sql
-- LOW-18: Migrate existing lowercase estado values in anticipo and devolucion tables
-- to SCREAMING_SNAKE_CASE so they match @Enumerated(EnumType.STRING) enum constant names.
--
-- Before: 'registrado', 'aplicado', 'devuelto', 'pendiente', 'procesada', 'rechazada'
-- After:  'REGISTRADO', 'APLICADO', 'DEVUELTO', 'PENDIENTE', 'PROCESADA', 'RECHAZADA'
--
-- UPPER() is idempotent — safe to re-apply if migration is re-run on a partially-
-- migrated database.

UPDATE anticipo   SET estado = UPPER(estado);
UPDATE devolucion SET estado = UPPER(estado);
