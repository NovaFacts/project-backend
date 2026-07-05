package com.novafacts.backend.reservation.repository;

import com.novafacts.backend.reservation.entity.Reservation;
import com.novafacts.backend.reservation.entity.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    long countByStatus(ReservationStatus status);

    // Spring Data treats "In" as the IN-operator suffix, so the derived name
    // findByClienteEmailAndCheckIn cannot be used. Explicit JPQL avoids the collision.
    @Query("SELECT r FROM Reservation r WHERE r.clienteEmail = :email AND r.checkIn = :checkIn")
    Optional<Reservation> findByClienteEmailAndCheckInDate(
            @Param("email") String email,
            @Param("checkIn") LocalDate checkIn);


    // Overlap condition: existing.checkIn < newCheckOut AND existing.checkOut > newCheckIn.
    // Parameter order is intentional: checkOut is the bound for CheckInBefore,
    // checkIn is the bound for CheckOutAfter. Only CONFIRMED reservations block availability.
    boolean existsByPropertyIdAndCheckInBeforeAndCheckOutAfterAndStatus(
            Long propertyId, LocalDate checkOut, LocalDate checkIn, ReservationStatus status);

    boolean existsByPropertyIdAndCheckInBeforeAndCheckOutAfterAndStatusAndIdNot(
            Long propertyId, LocalDate checkOut, LocalDate checkIn, ReservationStatus status, Long id);

    @Query("SELECT COUNT(r) > 0 FROM Reservation r WHERE r.temporada.id = :temporadaId")
    boolean existsByTemporadaId(@Param("temporadaId") Integer temporadaId);

    @Query("SELECT COUNT(r) > 0 FROM Reservation r WHERE r.politicaCancelacion.id = :politicaId")
    boolean existsByPoliticaCancelacionId(@Param("politicaId") Integer politicaId);

    @Query("SELECT COUNT(r) > 0 FROM Reservation r WHERE r.canal.id = :canalId")
    boolean existsByCanalId(@Param("canalId") Integer canalId);

    /**
     * Phase 4 (Issue 9): fetches canal, temporada, politicaCancelacion and usuarioCreador
     * in the same query instead of a separate subsequent-select per distinct value, now
     * that these associations are LAZY. All four are required @ManyToOne (to-one), so
     * JOIN FETCH cannot multiply rows and is safe to combine with Pageable.
     */
    @Query(
        value = """
            SELECT r
            FROM Reservation r
            JOIN FETCH r.canal
            JOIN FETCH r.temporada
            JOIN FETCH r.politicaCancelacion
            JOIN FETCH r.usuarioCreador
            """,
        countQuery = """
            SELECT COUNT(r)
            FROM Reservation r
            """
    )
    Page<Reservation> findAllWithAssociations(Pageable pageable);
}
