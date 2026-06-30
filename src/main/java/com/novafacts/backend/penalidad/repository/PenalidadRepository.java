package com.novafacts.backend.penalidad.repository;

import com.novafacts.backend.penalidad.entity.Penalidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PenalidadRepository extends JpaRepository<Penalidad, Long> {

    @Query("SELECT p FROM Penalidad p WHERE p.reserva.id = :reservaId")
    List<Penalidad> findByReservaId(@Param("reservaId") Long reservaId);

    @Query("SELECT COUNT(p) > 0 FROM Penalidad p WHERE p.reserva.id = :reservaId")
    boolean existsByReservaId(@Param("reservaId") Long reservaId);
}
