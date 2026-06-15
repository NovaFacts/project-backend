package com.novafacts.backend.booking.service;

import com.novafacts.backend.booking.model.Booking;

public class InvoiceCalculator {

    private static final double TAX_RATE = 0.19;
    private static final double LONG_STAY_DISCOUNT_RATE = 0.10;
    private static final int LONG_STAY_THRESHOLD_NIGHTS = 7;

    public double calculateSubtotal(Booking booking) {
        if (booking == null) {
            throw new IllegalArgumentException("La reserva no puede ser nula");
        }
        return booking.getNumberOfNights() * booking.getPricePerNight();
    }


    public double calculateTax(double subtotal) {
        if (subtotal < 0) {
            throw new IllegalArgumentException("El subtotal no puede ser negativo");
        }
        return subtotal * TAX_RATE;
    }


    public double calculateDiscount(Booking booking) {
        if (booking == null) {
            throw new IllegalArgumentException("La reserva no puede ser nula");
        }
        if (booking.getNumberOfNights() >= LONG_STAY_THRESHOLD_NIGHTS) {
            return calculateSubtotal(booking) * LONG_STAY_DISCOUNT_RATE;
        }
        return 0.0;
    }

    public double calculateTotal(Booking booking) {
        double subtotal = calculateSubtotal(booking);
        double tax      = calculateTax(subtotal);
        double discount = calculateDiscount(booking);
        return subtotal + tax - discount;
    }
}