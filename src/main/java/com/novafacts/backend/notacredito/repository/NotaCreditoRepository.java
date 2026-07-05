package com.novafacts.backend.notacredito.repository;

import com.novafacts.backend.notacredito.entity.NotaCredito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotaCreditoRepository extends JpaRepository<NotaCredito, Long> {

    @Query("SELECT nc FROM NotaCredito nc WHERE nc.factura.id = :facturaId")
    List<NotaCredito> findByFacturaId(@Param("facturaId") Long facturaId);

    /**
     * Issue 9 (N+1 remediation, final module): fetches factura and usuario in the same
     * query instead of a separate subsequent-select per distinct value, now that both
     * associations are LAZY. Both are required @ManyToOne (to-one), so JOIN FETCH cannot
     * multiply rows and is safe to combine with Pageable — same reasoning already
     * verified for AnticipoRepository.findAllWithUsuario() and
     * ReservationRepository.findAllWithAssociations().
     */
    @Query(
        value = "SELECT nc FROM NotaCredito nc JOIN FETCH nc.factura JOIN FETCH nc.usuario",
        countQuery = "SELECT COUNT(nc) FROM NotaCredito nc"
    )
    Page<NotaCredito> findAllWithAssociations(Pageable pageable);
}
