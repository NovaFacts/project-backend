package com.novafacts.backend.property.repository;

import com.novafacts.backend.property.entity.Property;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PropertyRepository extends JpaRepository<Property, Long> {

    List<Property> findByActivaTrue();

    Optional<Property> findByName(String name);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    /**
     * Acquires a row-level exclusive lock (SELECT ... FOR UPDATE) on the propiedad row.
     * Used by ReservationService before the availability overlap check to serialize
     * concurrent reservation creation/update for the same property.
     * Must be called inside an active @Transactional context; the lock is held until
     * the surrounding transaction commits or rolls back.
     */
    @Query("SELECT p FROM Property p WHERE p.id = :id")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Property> findByIdForUpdate(@Param("id") Long id);
}
