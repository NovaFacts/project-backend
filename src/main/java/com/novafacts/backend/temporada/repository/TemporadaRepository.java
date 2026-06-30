package com.novafacts.backend.temporada.repository;

import com.novafacts.backend.temporada.entity.Temporada;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TemporadaRepository extends JpaRepository<Temporada, Integer> {

    Optional<Temporada> findByNombre(String nombre);
}
