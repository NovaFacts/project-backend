package com.novafacts.backend.booking.service;

import com.novafacts.backend.booking.model.Booking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InvoiceCalculator — subtotal, tax, discount and total")
class InvoiceCalculatorTest {

    private InvoiceCalculator calculator;
    private LocalDate tomorrow;

    @BeforeEach
    void setUp() {
        calculator = new InvoiceCalculator();
        tomorrow = LocalDate.now().plusDays(1);
    }

    @Test
    @DisplayName("Subtotal = nights x price (3 nights at $200,000)")
    void calculateSubtotal_threeNights_returnsCorrectAmount() {
        Booking booking = new Booking("Carlos Pérez", tomorrow, tomorrow.plusDays(3), 200_000, 2);
        assertEquals(600_000, calculator.calculateSubtotal(booking), 0.01);
    }

    @Test
    @DisplayName("Subtotal for a one-night stay (minimum case)")
    void calculateSubtotal_oneNight_returnsNightlyRate() {
        Booking booking = new Booking("Ana Gómez", tomorrow, tomorrow.plusDays(1), 150_000, 1);
        assertEquals(150_000, calculator.calculateSubtotal(booking), 0.01);
    }

    @Test
    @DisplayName("Subtotal with a decimal nightly price")
    void calculateSubtotal_decimalPrice_roundsCorrectly() {
        Booking booking = new Booking("Luis Torres", tomorrow, tomorrow.plusDays(2), 99_999.50, 1);
        assertEquals(199_999.0, calculator.calculateSubtotal(booking), 0.01);
    }

    @Test
    @DisplayName("Null booking throws IllegalArgumentException")
    void calculateSubtotal_nullBooking_throws() {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculateSubtotal(null));
    }

    @Test
    @DisplayName("Tax is 19% of subtotal")
    void calculateTax_normalSubtotal_returns19Percent() {
        assertEquals(19_000, calculator.calculateTax(100_000), 0.01);
    }

    @Test
    @DisplayName("Tax on a zero subtotal is zero")
    void calculateTax_zeroSubtotal_returnsZero() {
        assertEquals(0, calculator.calculateTax(0), 0.01);
    }

    @Test
    @DisplayName("Negative subtotal throws IllegalArgumentException")
    void calculateTax_negativeSubtotal_throws() {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculateTax(-1));
    }

    @Test
    @DisplayName("No discount for 6-night stay (one below the threshold)")
    void calculateDiscount_sixNights_returnsZero() {
        Booking booking = new Booking("María López", tomorrow, tomorrow.plusDays(6), 100_000, 2);
        assertEquals(0.0, calculator.calculateDiscount(booking), 0.01);
    }

    @Test
    @DisplayName("10% discount applies at exactly 7 nights (threshold boundary)")
    void calculateDiscount_sevenNights_returnsTenPercent() {
        Booking booking = new Booking("Pedro Ramos", tomorrow, tomorrow.plusDays(7), 100_000, 2);
        assertEquals(70_000, calculator.calculateDiscount(booking), 0.01);
    }

    @Test
    @DisplayName("10% discount applies for stays longer than 7 nights")
    void calculateDiscount_fourteenNights_returnsTenPercent() {
        Booking booking = new Booking("Sofia Vargas", tomorrow, tomorrow.plusDays(14), 200_000, 3);
        assertEquals(280_000, calculator.calculateDiscount(booking), 0.01);
    }

    @Test
    @DisplayName("Total without discount: subtotal + 19% IVA (3 nights)")
    void calculateTotal_threeNights_noDiscount() {
        Booking booking = new Booking("Jorge Díaz", tomorrow, tomorrow.plusDays(3), 100_000, 1);
        assertEquals(357_000, calculator.calculateTotal(booking), 0.01);
    }

    @Test
    @DisplayName("Total with long-stay discount: subtotal + IVA - 10% (7 nights)")
    void calculateTotal_sevenNights_withDiscount() {
        Booking booking = new Booking("Laura Morales", tomorrow, tomorrow.plusDays(7), 100_000, 2);
        assertEquals(763_000, calculator.calculateTotal(booking), 0.01);
    }
}
