package com.novafacts.backend.anticipo.repository;

import com.novafacts.backend.anticipo.entity.Anticipo;
import com.novafacts.backend.anticipo.entity.AnticipoEstado;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AnticipoRepository extends JpaRepository<Anticipo, Long> {

    @Query("SELECT a FROM Anticipo a WHERE a.reserva.id = :reservaId")
    List<Anticipo> findByReservaId(@Param("reservaId") Long reservaId);

    @Query("SELECT SUM(a.monto) FROM Anticipo a")
    BigDecimal sumTotalMonto();

    @Query("SELECT COUNT(a) > 0 FROM Anticipo a WHERE a.reserva.id = :reservaId")
    boolean existsByReservaId(@Param("reservaId") Long reservaId);

    /**
     * RF11/RF12: candidate ids for server-side aggregation in FacturaService.create() —
     * every anticipo currently REGISTRADO for the reservation being invoiced. Unlocked;
     * each id is individually locked afterward via findByIdForUpdate() and re-checked,
     * so this query only needs to identify candidates, not guarantee they're still
     * REGISTRADO by the time the caller acts on them.
     */
    @Query("SELECT a.id FROM Anticipo a WHERE a.reserva.id = :reservaId AND a.estado = :estado")
    List<Long> findIdsByReservaIdAndEstado(@Param("reservaId") Long reservaId, @Param("estado") AnticipoEstado estado);

    /**
     * C-2: Acquires a row-level exclusive lock (SELECT ... FOR UPDATE) on the anticipo row.
     * Used by FacturaService.applyAnticipo() and DevolucionService.create() before their
     * REGISTRADO-state check, so a factura discount and a devolucion cannot both apply the
     * same advance payment concurrently. Mirrors PropertyRepository.findByIdForUpdate().
     * Must be called inside an active @Transactional context; the lock is held until the
     * surrounding transaction commits or rolls back.
     */
    @Query("SELECT a FROM Anticipo a WHERE a.id = :id")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Anticipo> findByIdForUpdate(@Param("id") Long id);

    /**
     * Phase 3 (Issue 9): fetches usuario in the same query instead of a separate
     * subsequent-select per distinct usuario, now that Anticipo.usuario is LAZY.
     * usuario is a required @ManyToOne (to-one), so JOIN FETCH cannot multiply rows
     * and is safe to combine with Pageable.
     */
    @Query(
        value = "SELECT a FROM Anticipo a JOIN FETCH a.usuario",
        countQuery = "SELECT COUNT(a) FROM Anticipo a"
    )
    Page<Anticipo> findAllWithUsuario(Pageable pageable);
}
