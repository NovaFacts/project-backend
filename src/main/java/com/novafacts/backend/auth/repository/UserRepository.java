package com.novafacts.backend.auth.repository;

import com.novafacts.backend.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    /** MEDIUM-16: Returns only active (non-soft-deleted) users for the admin listing. */
    Page<User> findByActivoTrue(Pageable pageable);

    /**
     * C-3 (AUDIT_v5): lets callers find an existing user by role name without assuming
     * a specific email — used by DevelopmentDataSeeder to locate the administrator
     * AdminUserInitializer already created, instead of hardcoding an admin identity
     * of its own. Returns a List (not a single Optional) so this can never throw
     * NonUniqueResultException if more than one user ever holds the same role.
     */
    @Query("SELECT u FROM User u WHERE u.rol.nombre = :rolNombre ORDER BY u.id ASC")
    List<User> findAllByRolNombreOrderById(@Param("rolNombre") String rolNombre);
}
