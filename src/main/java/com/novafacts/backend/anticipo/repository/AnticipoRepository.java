package com.novafacts.backend.anticipo.repository;

import com.novafacts.backend.anticipo.entity.Anticipo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface AnticipoRepository extends JpaRepository<Anticipo, Long> {

    @Query("SELECT a FROM Anticipo a WHERE a.reserva.id = :reservaId")
    List<Anticipo> findByReservaId(@Param("reservaId") Long reservaId);

    @Query("SELECT SUM(a.monto) FROM Anticipo a")
    BigDecimal sumTotalMonto();

    @Query("SELECT COUNT(a) > 0 FROM Anticipo a WHERE a.reserva.id = :reservaId")
    boolean existsByReservaId(@Param("reservaId") Long reservaId);
}
