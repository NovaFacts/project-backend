package com.novafacts.backend.devolucion.repository;

import com.novafacts.backend.devolucion.entity.Devolucion;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DevolucionRepository extends JpaRepository<Devolucion, Long> {

    @Query("SELECT d FROM Devolucion d WHERE d.reserva.id = :reservaId")
    List<Devolucion> findByReservaId(@Param("reservaId") Long reservaId);

    @Query("SELECT COUNT(d) > 0 FROM Devolucion d WHERE d.reserva.id = :reservaId")
    boolean existsByReservaId(@Param("reservaId") Long reservaId);

    /**
     * C-2 (AUDIT_v5): Acquires a row-level exclusive lock (SELECT ... FOR UPDATE) on the
     * devolucion row. Used by procesar()/rechazar()/delete() before their PENDIENTE-state
     * check, so two concurrent state-transition requests on the same refund cannot both
     * read the same initial state and both succeed with contradictory outcomes. Mirrors
     * AnticipoRepository.findByIdForUpdate()/PropertyRepository.findByIdForUpdate().
     * Must be called inside an active @Transactional context; the lock is held until the
     * surrounding transaction commits or rolls back.
     */
    @Query("SELECT d FROM Devolucion d WHERE d.id = :id")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Devolucion> findByIdForUpdate(@Param("id") Long id);

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
