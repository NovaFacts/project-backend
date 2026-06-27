package com.novafacts.backend.invoice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class CreateInvoiceRequest {

    @NotNull(message = "El identificador de la reserva es obligatorio")
    @Positive(message = "El identificador de la reserva debe ser mayor a cero")
    private Long reservationId;

    public CreateInvoiceRequest() {}

    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }
}
