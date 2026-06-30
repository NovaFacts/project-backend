-- V9__add_fk_reserva_propiedad.sql
-- HIGH-8 (Production_Readiness_Audit_v5): reserva.propiedad_id existed since V1 as
-- BIGINT NOT NULL with only an index. No FOREIGN KEY constraint was ever declared,
-- leaving the column unguarded at the database level.
--
-- This migration adds the missing FK. The constraint allows referential integrity to
-- be enforced by PostgreSQL rather than relying solely on the application-layer check
-- (propertyRepository.existsById) in ReservationService.
--
-- PRE-CONDITION: All existing reserva rows must have a propiedad_id that refers to an
-- existing propiedad row. In normal operation (data seeded via DevelopmentDataSeeder)
-- this is guaranteed. If orphan rows are present, this migration will fail; clean them
-- up before applying:
--   DELETE FROM reserva WHERE propiedad_id NOT IN (SELECT id FROM propiedad);

ALTER TABLE reserva
    ADD CONSTRAINT fk_reserva_propiedad
        FOREIGN KEY (propiedad_id) REFERENCES propiedad(id);
