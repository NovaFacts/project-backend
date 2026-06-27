package com.novafacts.backend.reservation.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "reserva",
    indexes = {
        @Index(name = "idx_reserva_propiedad_id", columnList = "propiedad_id")
    }
)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "huesped_id", nullable = false)
    private Long guestId;

    @Column(name = "propiedad_id", nullable = false)
    private Long propertyId;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate checkIn;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate checkOut;

    @Column(name = "cantidad_huespedes", nullable = false)
    private Integer guestCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Reservation() {}

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public Long getGuestId() { return guestId; }
    public void setGuestId(Long guestId) { this.guestId = guestId; }

    public Long getPropertyId() { return propertyId; }
    public void setPropertyId(Long propertyId) { this.propertyId = propertyId; }

    public LocalDate getCheckIn() { return checkIn; }
    public void setCheckIn(LocalDate checkIn) { this.checkIn = checkIn; }

    public LocalDate getCheckOut() { return checkOut; }
    public void setCheckOut(LocalDate checkOut) { this.checkOut = checkOut; }

    public Integer getGuestCount() { return guestCount; }
    public void setGuestCount(Integer guestCount) { this.guestCount = guestCount; }

    public ReservationStatus getStatus() { return status; }
    public void setStatus(ReservationStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
