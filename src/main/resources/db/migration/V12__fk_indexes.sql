-- V12__fk_indexes.sql
-- C-1 (AUDIT_v3): PostgreSQL does not automatically create indexes on foreign-key columns.
-- Every FK constraint introduced in V2, V4, and V7 left the referencing column
-- unindexed, forcing sequential scans on every lookup that filters by that column.
--
-- This migration adds B-tree indexes on every unindexed FK column in the schema.
--
-- Already-indexed FK columns (not repeated here):
--   reserva.propiedad_id  → idx_reserva_propiedad_id (explicit, V1)
--   factura.reserva_id    → implicit index from UNIQUE constraint (V7)
--   factura.numero_factura → implicit index from UNIQUE constraint (V7)
--   nota_credito.numero_nota → implicit index from UNIQUE constraint (V7)
--
-- IF NOT EXISTS guards are intentional: they make the migration idempotent if Flyway
-- re-runs it after a partial failure, without requiring a Flyway repair command.
-- Supported since PostgreSQL 9.5; this project targets PostgreSQL 15.
--
-- NOTE ON CONCURRENCY: Standard CREATE INDEX briefly holds a ShareLock on the table.
-- For production deployments on tables with existing data use CREATE INDEX CONCURRENTLY
-- (which cannot run inside a transaction and requires Flyway outOfOrder or a manual step).
-- At the current scale of this project (class/dev data) ShareLock is acceptable.

-- ── Priority A: Application query paths ───────────────────────────────────────
-- These indexes directly serve JPQL queries in the repository layer:
--   AnticipoRepository  : findByReservaId, existsByReservaId
--   PenalidadRepository : findByReservaId, existsByReservaId
--   DevolucionRepository: findByReservaId, existsByReservaId
--   NotaCreditoRepository: findByFacturaId
--   PoliticaCancelacionRepository: findByPropiedadId (ADDITIONAL — not in original audit)

CREATE INDEX IF NOT EXISTS idx_anticipo_reserva_id
    ON anticipo (reserva_id);

CREATE INDEX IF NOT EXISTS idx_penalidad_reserva_id
    ON penalidad (reserva_id);

CREATE INDEX IF NOT EXISTS idx_devolucion_reserva_id
    ON devolucion (reserva_id);

CREATE INDEX IF NOT EXISTS idx_nota_credito_factura_id
    ON nota_credito (factura_id);

CREATE INDEX IF NOT EXISTS idx_politica_cancelacion_propiedad_id
    ON politica_cancelacion (propiedad_id);

-- ── Priority B: FK enforcement (parent DELETE / UPDATE checks) ─────────────────
-- PostgreSQL validates referential integrity by scanning child rows when a parent
-- row is deleted or updated. Without an index this is a full sequential scan of
-- the child table. These columns are not filtered on by application queries today
-- but protect performance when soft-delete or administrative delete operations occur.

-- anticipo → usuario
CREATE INDEX IF NOT EXISTS idx_anticipo_usuario_id
    ON anticipo (usuario_id);

-- penalidad → usuario
CREATE INDEX IF NOT EXISTS idx_penalidad_usuario_id
    ON penalidad (usuario_id);

-- factura → usuario  (factura.reserva_id already covered by UNIQUE constraint index)
CREATE INDEX IF NOT EXISTS idx_factura_usuario_id
    ON factura (usuario_id);

-- nota_credito → usuario
CREATE INDEX IF NOT EXISTS idx_nota_credito_usuario_id
    ON nota_credito (usuario_id);

-- devolucion → anticipo  (also benefits any future query filtering devoluciones by anticipo)
CREATE INDEX IF NOT EXISTS idx_devolucion_anticipo_id
    ON devolucion (anticipo_id);

-- devolucion → usuario
CREATE INDEX IF NOT EXISTS idx_devolucion_usuario_id
    ON devolucion (usuario_id);

-- reserva FK columns added in V4 (ADDITIONAL — not in original audit)
-- These protect against sequential scans of reserva when canal/temporada/
-- politica_cancelacion/usuario rows are deleted.
CREATE INDEX IF NOT EXISTS idx_reserva_canal_id
    ON reserva (canal_id);

CREATE INDEX IF NOT EXISTS idx_reserva_temporada_id
    ON reserva (temporada_id);

CREATE INDEX IF NOT EXISTS idx_reserva_politica_cancelacion_id
    ON reserva (politica_cancelacion_id);

CREATE INDEX IF NOT EXISTS idx_reserva_usuario_creador_id
    ON reserva (usuario_creador_id);

-- usuario.rol_id (V2 FK, ADDITIONAL — not in original audit)
-- V8 seed query uses WHERE rol_id = 1; FK enforcement checks scan usuario when
-- a rol row is deleted (rare, but unbounded without an index).
CREATE INDEX IF NOT EXISTS idx_usuario_rol_id
    ON usuario (rol_id);
