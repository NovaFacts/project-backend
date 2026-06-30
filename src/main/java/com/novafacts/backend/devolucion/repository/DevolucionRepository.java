package com.novafacts.backend.devolucion.repository;

import com.novafacts.backend.devolucion.entity.Devolucion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DevolucionRepository extends JpaRepository<Devolucion, Long> {

    @Query("SELECT d FROM Devolucion d WHERE d.reserva.id = :reservaId")
    List<Devolucion> findByReservaId(@Param("reservaId") Long reservaId);

    @Query("SELECT COUNT(d) > 0 FROM Devolucion d WHERE d.reserva.id = :reservaId")
    boolean existsByReservaId(@Param("reservaId") Long reservaId);
}
