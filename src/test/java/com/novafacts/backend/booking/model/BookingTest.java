package com.novafacts.backend.booking.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Booking — constructor and night calculation")
class BookingTest {

    private final LocalDate tomorrow = LocalDate.now().plusDays(1);

    @Test
    @DisplayName("Valid data creates a booking with correct field values")
    void booking_validData_createsCorrectly() {
        Booking booking = new Booking("Carlos Pérez", tomorrow, tomorrow.plusDays(3), 200_000, 2);

        assertAll(
                () -> assertEquals("Carlos Pérez", booking.getGuestName()),
                () -> assertEquals(3, booking.getNumberOfNights()),
                () -> assertEquals(200_000, booking.getPricePerNight(), 0.01),
                () -> assertEquals(2, booking.getNumberOfGuests())
        );
    }

    @Test
    @DisplayName("Night count matches the number of days between check-in and check-out")
    void getNumberOfNights_fiveNights_returnsFive() {
        Booking booking = new Booking("Ana Gómez", tomorrow, tomorrow.plusDays(5), 100_000, 1);
        assertEquals(5, booking.getNumberOfNights());
    }

    @Test
    @DisplayName("A one-night stay (minimum valid) is accepted")
    void booking_oneNight_isValid() {
        assertDoesNotThrow(() ->
                new Booking("Luis Torres", tomorrow, tomorrow.plusDays(1), 80_000, 1)
        );
    }

    @Test
    @DisplayName("Blank guest name throws IllegalArgumentException")
    void booking_blankName_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new Booking("   ", tomorrow, tomorrow.plusDays(2), 100_000, 1)
        );
    }

    @Test
    @DisplayName("checkOut equal to checkIn (0 nights) throws IllegalArgumentException")
    void booking_checkOutEqualsCheckIn_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new Booking("Pedro Ramos", tomorrow, tomorrow, 100_000, 1)
        );
    }

    @Test
    @DisplayName("checkOut before checkIn throws IllegalArgumentException")
    void booking_checkOutBeforeCheckIn_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new Booking("María López", tomorrow, tomorrow.minusDays(1), 100_000, 1)
        );
    }

    @Test
    @DisplayName("Price of zero throws IllegalArgumentException")
    void booking_zeroPricePerNight_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new Booking("Jorge Díaz", tomorrow, tomorrow.plusDays(2), 0, 1)
        );
    }

    @Test
    @DisplayName("Negative price throws IllegalArgumentException")
    void booking_negativePricePerNight_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new Booking("Sofia Vargas", tomorrow, tomorrow.plusDays(2), -50_000, 1)
        );
    }

    @Test
    @DisplayName("Zero guests throws IllegalArgumentException")
    void booking_zeroGuests_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new Booking("Laura Morales", tomorrow, tomorrow.plusDays(2), 100_000, 0)
        );
    }
}