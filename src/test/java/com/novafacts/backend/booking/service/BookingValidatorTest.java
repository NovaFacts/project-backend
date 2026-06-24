package com.novafacts.backend.booking.service;

import com.novafacts.backend.booking.model.Booking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BookingValidator — business rules and date conflict detection")
class BookingValidatorTest {

    private BookingValidator validator;
    private LocalDate tomorrow;

    @BeforeEach
    void setUp() {
        validator = new BookingValidator();
        tomorrow = LocalDate.now().plusDays(1);
    }

    @Test
    @DisplayName("Valid booking returns true")
    void validate_validBooking_returnsTrue() {
        Booking booking = new Booking("Carlos Pérez", tomorrow, tomorrow.plusDays(3), 100_000, 2);
        assertTrue(validator.validate(booking));
    }

    @Test
    @DisplayName("Exactly 4 guests is accepted (upper boundary)")
    void validate_fourGuests_isValid() {
        Booking booking = new Booking("Familia García", tomorrow, tomorrow.plusDays(2), 250_000, 4);
        assertTrue(validator.validate(booking));
    }

    @Test
    @DisplayName("5 guests exceeds the limit and throws IllegalArgumentException")
    void validate_fiveGuests_throws() {
        Booking booking = new Booking("Grupo Grande", tomorrow, tomorrow.plusDays(2), 250_000, 5);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(booking));
    }

    @Test
    @DisplayName("Exactly 30 nights is accepted (upper boundary)")
    void validate_thirtyNights_isValid() {
        Booking booking = new Booking("Ana López", tomorrow, tomorrow.plusDays(30), 80_000, 1);
        assertTrue(validator.validate(booking));
    }

    @Test
    @DisplayName("31 nights exceeds the maximum stay and throws IllegalArgumentException")
    void validate_thirtyOneNights_throws() {
        Booking booking = new Booking("Pedro Ruiz", tomorrow, tomorrow.plusDays(31), 80_000, 1);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(booking));
    }

    @Test
    @DisplayName("Null booking throws IllegalArgumentException")
    void validate_nullBooking_throws() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(null));
    }

    @Test
    @DisplayName("Partially overlapping bookings are in conflict")
    void hasDateConflict_overlap_returnsTrue() {
        Booking b1 = new Booking("Huésped A", tomorrow, tomorrow.plusDays(5), 100_000, 1);
        Booking b2 = new Booking("Huésped B", tomorrow.plusDays(3), tomorrow.plusDays(7), 100_000, 1);
        assertTrue(validator.hasDateConflict(b1, b2));
    }

    @Test
    @DisplayName("Same-day checkout/check-in is NOT a conflict")
    void hasDateConflict_adjacentDates_returnsFalse() {
        Booking b1 = new Booking("Huésped A", tomorrow, tomorrow.plusDays(5), 100_000, 1);
        Booking b2 = new Booking("Huésped B", tomorrow.plusDays(5), tomorrow.plusDays(8), 100_000, 1);
        assertFalse(validator.hasDateConflict(b1, b2));
    }

    @Test
    @DisplayName("Completely separate bookings have no conflict")
    void hasDateConflict_separatedBookings_returnsFalse() {
        Booking b1 = new Booking("Huésped A", tomorrow, tomorrow.plusDays(3), 100_000, 1);
        Booking b2 = new Booking("Huésped B", tomorrow.plusDays(10), tomorrow.plusDays(13), 100_000, 1);
        assertFalse(validator.hasDateConflict(b1, b2));
    }

    @Test
    @DisplayName("Null second booking throws IllegalArgumentException")
    void hasDateConflict_nullBooking_throws() {
        Booking b1 = new Booking("Huésped A", tomorrow, tomorrow.plusDays(3), 100_000, 1);
        assertThrows(IllegalArgumentException.class, () -> validator.hasDateConflict(b1, null));
    }
}
