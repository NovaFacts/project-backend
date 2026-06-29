package com.novafacts.backend.invoice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public class CreateInvoiceRequest {

    @NotNull(message = "El identificador de la reserva es obligatorio")
    @Positive(message = "El identificador de la reserva debe ser mayor a cero")
    private Long reservationId;

    @NotNull(message = "El subtotal es obligatorio")
    @DecimalMin(value = "0.01", message = "El subtotal debe ser mayor a cero")
    private BigDecimal subtotal;

    public CreateInvoiceRequest() {}

    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
}
