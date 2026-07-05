package com.novafacts.backend.penalidad.repository;

import com.novafacts.backend.penalidad.entity.Penalidad;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PenalidadRepository extends JpaRepository<Penalidad, Long> {

    @Query("SELECT p FROM Penalidad p WHERE p.reserva.id = :reservaId")
    List<Penalidad> findByReservaId(@Param("reservaId") Long reservaId);

    @Query("SELECT COUNT(p) > 0 FROM Penalidad p WHERE p.reserva.id = :reservaId")
    boolean existsByReservaId(@Param("reservaId") Long reservaId);

    /**
     * Phase 3 (Issue 9): fetches usuario in the same query instead of a separate
     * subsequent-select per distinct usuario, now that Penalidad.usuario is LAZY.
     * usuario is a required @ManyToOne (to-one), so JOIN FETCH cannot multiply rows
     * and is safe to combine with Pageable.
     */
    @Query(value = "SELECT p FROM Penalidad p JOIN FETCH p.usuario",
           countQuery = "SELECT COUNT(p) FROM Penalidad p")
    Page<Penalidad> findAllWithUsuario(Pageable pageable);
}
