package com.novafacts.backend.booking.service;

import com.novafacts.backend.booking.model.Booking;
import java.time.LocalDate;

public class BookingValidator {

    private static final int MAX_GUESTS_PER_ROOM = 4;
    private static final int MAX_STAY_NIGHTS = 30;


    public boolean validate(Booking booking) {
        if (booking == null) {
            throw new IllegalArgumentException("La reserva no puede ser nula");
        }
        if (booking.getCheckIn().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("No se puede reservar en fechas pasadas");
        }
        if (booking.getNumberOfGuests() > MAX_GUESTS_PER_ROOM) {
            throw new IllegalArgumentException(
                    "Máximo " + MAX_GUESTS_PER_ROOM + " huéspedes por habitación");
        }
        if (booking.getNumberOfNights() > MAX_STAY_NIGHTS) {
            throw new IllegalArgumentException(
                    "La estadía no puede superar " + MAX_STAY_NIGHTS + " noches");
        }
        return true;
    }


    public boolean hasDateConflict(Booking b1, Booking b2) {
        if (b1 == null || b2 == null) {
            throw new IllegalArgumentException("Las reservas no pueden ser nulas");
        }
        return b1.getCheckIn().isBefore(b2.getCheckOut()) &&
                b2.getCheckIn().isBefore(b1.getCheckOut());
    }
}
