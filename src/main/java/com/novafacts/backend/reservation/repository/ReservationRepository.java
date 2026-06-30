package com.novafacts.backend.reservation.repository;

import com.novafacts.backend.reservation.entity.Reservation;
import com.novafacts.backend.reservation.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    long countByStatus(ReservationStatus status);

    List<Reservation> findByClienteEmail(String clienteEmail);

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
}
