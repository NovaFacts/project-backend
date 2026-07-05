package com.novafacts.backend.factura.repository;

import com.novafacts.backend.factura.entity.Factura;
import com.novafacts.backend.invoice.entity.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
