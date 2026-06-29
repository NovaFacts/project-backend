package com.novafacts.backend.factura.repository;

import com.novafacts.backend.factura.entity.Factura;
import com.novafacts.backend.invoice.entity.InvoiceStatus;
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
}
