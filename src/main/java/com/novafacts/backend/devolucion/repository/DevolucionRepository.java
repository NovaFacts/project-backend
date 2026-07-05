package com.novafacts.backend.devolucion.repository;

import com.novafacts.backend.devolucion.entity.Devolucion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DevolucionRepository extends JpaRepository<Devolucion, Long> {

    @Query("SELECT d FROM Devolucion d WHERE d.reserva.id = :reservaId")
    List<Devolucion> findByReservaId(@Param("reservaId") Long reservaId);

    @Query("SELECT COUNT(d) > 0 FROM Devolucion d WHERE d.reserva.id = :reservaId")
    boolean existsByReservaId(@Param("reservaId") Long reservaId);

    /**
     * Phase 3 (Issue 9): fetches usuario in the same query instead of a separate
     * subsequent-select per distinct usuario, now that Devolucion.usuario is LAZY.
     * usuario is a required @ManyToOne (to-one), so JOIN FETCH cannot multiply rows
     * and is safe to combine with Pageable.
     */
    @Query(value = "SELECT d FROM Devolucion d JOIN FETCH d.usuario",
           countQuery = "SELECT COUNT(d) FROM Devolucion d")
    Page<Devolucion> findAllWithUsuario(Pageable pageable);
}
