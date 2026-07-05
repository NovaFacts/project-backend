package com.novafacts.backend.canal.repository;

import com.novafacts.backend.canal.entity.Canal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CanalRepository extends JpaRepository<Canal, Integer> {

    // L-7: case-insensitive existence checks backing the DB-level UNIQUE constraint
    // on canal.nombre (mirrors TemporadaRepository/PropertyRepository's pattern).
    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Integer id);
}
