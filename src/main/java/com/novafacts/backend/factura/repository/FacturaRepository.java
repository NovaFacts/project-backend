package com.novafacts.backend.factura.repository;

import com.novafacts.backend.factura.entity.Factura;
import com.novafacts.backend.invoice.entity.InvoiceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FacturaRepository extends JpaRepository<Factura, Long> {

    @Query("SELECT COUNT(f) > 0 FROM Factura f WHERE f.reserva.id = :reservaId")
    boolean existsByReservaId(@Param("reservaId") Long reservaId);

    @Query("SELECT f FROM Factura f WHERE f.reserva.id = :reservaId")
    Optional<Factura> findByReservaId(@Param("reservaId") Long reservaId);

    long countByEstado(InvoiceStatus estado);

    /**
     * C-2 (AUDIT_v5): Acquires a row-level exclusive lock (SELECT ... FOR UPDATE) on the
     * factura row. Used by emitir()/anular() before their PENDING-state check, so two
     * concurrent state-transition requests on the same invoice cannot both read the same
     * initial state and both succeed with contradictory outcomes. Mirrors
     * AnticipoRepository.findByIdForUpdate()/PropertyRepository.findByIdForUpdate().
     * Must be called inside an active @Transactional context; the lock is held until the
     * surrounding transaction commits or rolls back.
     */
    @Query("SELECT f FROM Factura f WHERE f.id = :id")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Factura> findByIdForUpdate(@Param("id") Long id);

    /**
     * Phase 3 (Issue 9): fetches usuario in the same query instead of a separate
     * subsequent-select per distinct usuario, now that Factura.usuario is LAZY.
     * usuario is a required @ManyToOne (to-one), so JOIN FETCH cannot multiply rows
     * and is safe to combine with Pageable.
     */
    @Query(
        value = "SELECT f FROM Factura f JOIN FETCH f.usuario",
        countQuery = "SELECT COUNT(f) FROM Factura f"
    )
    Page<Factura> findAllWithUsuario(Pageable pageable);
}
