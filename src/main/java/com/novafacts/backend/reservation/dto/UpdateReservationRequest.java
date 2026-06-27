package com.novafacts.backend.reservation.dto;

import com.novafacts.backend.reservation.entity.ReservationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public class UpdateReservationRequest {

    @NotNull(message = "El identificador del huésped es obligatorio")
    @Positive(message = "El identificador del huésped debe ser mayor a cero")
    private Long guestId;

    @NotNull(message = "El identificador de la propiedad es obligatorio")
    @Positive(message = "El identificador de la propiedad debe ser mayor a cero")
    private Long propertyId;

    @NotNull(message = "La fecha de inicio es obligatoria")
    private LocalDate checkIn;

    @NotNull(message = "La fecha de fin es obligatoria")
    private LocalDate checkOut;

    @NotNull(message = "La cantidad de huéspedes es obligatoria")
    @Positive(message = "La cantidad de huéspedes debe ser mayor a cero")
    private Integer guestCount;

    @NotNull(message = "El estado es obligatorio")
    private ReservationStatus status;

    public UpdateReservationRequest() {}

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
}
