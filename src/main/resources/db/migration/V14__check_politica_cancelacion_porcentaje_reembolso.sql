-- V14__check_politica_cancelacion_porcentaje_reembolso.sql
-- L-4: Enforce the valid business range on politica_cancelacion.porcentaje_reembolso
-- at the database level.
--
-- History: V4 created politica_cancelacion.porcentaje_reembolso as a plain
-- DECIMAL(5,2) with no range constraint. PoliticaCancelacionRequest already
-- validates 0.00 <= porcentaje_reembolso <= 100.00 via Bean Validation, but that
-- only guards the HTTP request path — writes that bypass the DTO (e.g. seed data,
-- manual SQL, future imports) are not covered. This CHECK constraint closes that
-- gap and guarantees the invariant regardless of write path.
--
-- NOTE: this migration will fail if rows with porcentaje_reembolso outside
-- [0, 100] already exist in the politica_cancelacion table. Resolve them manually
-- before applying if that occurs.
ALTER TABLE politica_cancelacion
    ADD CONSTRAINT chk_politica_porcentaje_reembolso
    CHECK (porcentaje_reembolso >= 0 AND porcentaje_reembolso <= 100);
