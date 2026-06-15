package com.novafacts.backend.booking.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
public class Booking {

    private final String guestName;
    private final LocalDate checkIn;
    private final LocalDate checkOut;
    private final double pricePerNight;
    private final int numberOfGuests;

    /**
     * @param guestName      full name of the guest — must not be blank
     * @param checkIn        arrival date
     * @param checkOut       departure date — must be strictly after checkIn
     * @param pricePerNight  room rate in COP — must be greater than zero
     * @param numberOfGuests number of guests — must be >= 1
     * @throws IllegalArgumentException if any parameter violates the rules above
     */
    public Booking(String guestName, LocalDate checkIn, LocalDate checkOut,
                   double pricePerNight, int numberOfGuests) {
        if (guestName == null || guestName.isBlank()) {
            throw new IllegalArgumentException("El nombre del huésped no puede estar vacío");
        }
        if (checkIn == null || checkOut == null) {
            throw new IllegalArgumentException("Las fechas no pueden ser nulas");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("La fecha de salida debe ser posterior a la de entrada");
        }
        if (pricePerNight <= 0) {
            throw new IllegalArgumentException("El precio por noche debe ser mayor a cero");
        }
        if (numberOfGuests <= 0) {
            throw new IllegalArgumentException("El número de huéspedes debe ser mayor a cero");
        }
        this.guestName = guestName;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.pricePerNight = pricePerNight;
        this.numberOfGuests = numberOfGuests;
    }

    /** Returns the number of nights between check-in and check-out. */
    public long getNumberOfNights() {
        return ChronoUnit.DAYS.between(checkIn, checkOut);
    }

    public String getGuestName()     { return guestName; }
    public LocalDate getCheckIn()    { return checkIn; }
    public LocalDate getCheckOut()   { return checkOut; }
    public double getPricePerNight() { return pricePerNight; }
    public int getNumberOfGuests()   { return numberOfGuests; }
}