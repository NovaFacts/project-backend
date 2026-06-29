package com.novafacts.backend.notacredito.repository;

import com.novafacts.backend.notacredito.entity.NotaCredito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotaCreditoRepository extends JpaRepository<NotaCredito, Long> {

    @Query("SELECT nc FROM NotaCredito nc WHERE nc.factura.id = :facturaId")
    List<NotaCredito> findByFacturaId(@Param("facturaId") Long facturaId);
}
