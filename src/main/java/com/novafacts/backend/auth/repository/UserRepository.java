package com.novafacts.backend.auth.repository;

import com.novafacts.backend.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    /** MEDIUM-16: Returns only active (non-soft-deleted) users for the admin listing. */
    Page<User> findByActivoTrue(Pageable pageable);
}
