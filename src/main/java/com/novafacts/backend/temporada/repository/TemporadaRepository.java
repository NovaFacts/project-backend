package com.novafacts.backend.temporada.repository;

import com.novafacts.backend.temporada.entity.Temporada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface TemporadaRepository extends JpaRepository<Temporada, Integer> {

    Optional<Temporada> findByNombre(String nombre);

    // L-3: case-insensitive existence checks backing the unique index added in
    // V13__unique_index_temporada_nombre_ci.sql (mirrors PropertyRepository's pattern).
    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Integer id);

    // Overlap condition mirrors ReservationRepository's property-overlap check:
    // existing.fechaInicio < new.fechaFin AND existing.fechaFin > new.fechaInicio.
    // Exclusive boundaries mean adjacent ranges (end == next start) do not overlap.
    @Query("SELECT COUNT(t) > 0 FROM Temporada t WHERE t.fechaInicio < :fin AND t.fechaFin > :inicio")
    boolean existsOverlap(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    @Query("SELECT COUNT(t) > 0 FROM Temporada t WHERE t.id <> :id AND t.fechaInicio < :fin AND t.fechaFin > :inicio")
    boolean existsOverlapExcludingId(
            @Param("inicio") LocalDate inicio,
            @Param("fin") LocalDate fin,
            @Param("id") Integer id);
}
