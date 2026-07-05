-- V15__check_temporada_fecha_rango.sql
-- L-8: Enforce the valid business range on temporada.fecha_inicio / fecha_fin
-- at the database level.
--
-- History: V3 created temporada.fecha_inicio and fecha_fin as plain DATE columns
-- with no ordering constraint. TemporadaService.validarFechas() already validates
-- fecha_inicio < fecha_fin via Bean Validation's cross-field service check, but
-- that only guards the HTTP request path — writes that bypass the service (e.g.
-- test fixtures and DevelopmentDataSeeder that call TemporadaRepository.save()
-- directly, manual SQL, future imports) are not covered. This CHECK constraint
-- closes that gap and guarantees the invariant regardless of write path.
--
-- Strict inequality (<) matches validarFechas() exactly, which rejects
-- fecha_inicio == fecha_fin as well as fecha_inicio > fecha_fin.
--
-- NOTE: this migration will fail if rows with fecha_inicio >= fecha_fin already
-- exist in the temporada table. Resolve them manually before applying if that
-- occurs.
ALTER TABLE temporada
    ADD CONSTRAINT chk_temporada_fecha_rango
    CHECK (fecha_inicio < fecha_fin);
