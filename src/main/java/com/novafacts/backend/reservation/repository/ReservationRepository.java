package com.novafacts.backend.reservation.repository;

import com.novafacts.backend.reservation.entity.Reservation;
import com.novafacts.backend.reservation.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    // Overlap condition: existing.checkIn < newCheckOut AND existing.checkOut > newCheckIn.
    // Parameter order is intentional: checkOut is the bound for CheckInBefore,
    // checkIn is the bound for CheckOutAfter. Only CONFIRMED reservations block availability.
    boolean existsByPropertyIdAndCheckInBeforeAndCheckOutAfterAndStatus(
            Long propertyId, LocalDate checkOut, LocalDate checkIn, ReservationStatus status);

    boolean existsByPropertyIdAndCheckInBeforeAndCheckOutAfterAndStatusAndIdNot(
            Long propertyId, LocalDate checkOut, LocalDate checkIn, ReservationStatus status, Long id);
}
