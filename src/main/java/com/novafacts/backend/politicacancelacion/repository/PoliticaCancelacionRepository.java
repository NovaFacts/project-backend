package com.novafacts.backend.politicacancelacion.repository;

import com.novafacts.backend.politicacancelacion.entity.PoliticaCancelacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PoliticaCancelacionRepository extends JpaRepository<PoliticaCancelacion, Integer> {

    List<PoliticaCancelacion> findByPropiedadId(Long propiedadId);
}
