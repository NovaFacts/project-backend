package com.novafacts.backend.reservation.dto;

import com.novafacts.backend.reservation.entity.ReservationStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ReservationResponse {

    private Long id;
    private Long guestId;
    private Long propertyId;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private Integer guestCount;
    private ReservationStatus status;
    private LocalDateTime createdAt;

    public ReservationResponse(Long id, Long guestId, Long propertyId,
                               LocalDate checkIn, LocalDate checkOut,
                               Integer guestCount, ReservationStatus status,
                               LocalDateTime createdAt) {
        this.id = id;
        this.guestId = guestId;
        this.propertyId = propertyId;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.guestCount = guestCount;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getGuestId() { return guestId; }
    public Long getPropertyId() { return propertyId; }
    public LocalDate getCheckIn() { return checkIn; }
    public LocalDate getCheckOut() { return checkOut; }
    public Integer getGuestCount() { return guestCount; }
    public ReservationStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
