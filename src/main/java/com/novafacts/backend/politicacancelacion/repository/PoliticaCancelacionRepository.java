package com.novafacts.backend.politicacancelacion.repository;

import com.novafacts.backend.politicacancelacion.entity.PoliticaCancelacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PoliticaCancelacionRepository extends JpaRepository<PoliticaCancelacion, Integer> {

    List<PoliticaCancelacion> findByPropiedadId(Long propiedadId);

    Optional<PoliticaCancelacion> findByNombre(String nombre);
}
