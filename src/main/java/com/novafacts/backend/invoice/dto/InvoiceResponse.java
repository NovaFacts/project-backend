package com.novafacts.backend.invoice.dto;

import com.novafacts.backend.invoice.entity.InvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class InvoiceResponse {

    private Long id;
    private Long reservationId;
    private BigDecimal subtotal;
    private BigDecimal tax;
    private BigDecimal total;
    private InvoiceStatus status;
    private LocalDateTime createdAt;

    public InvoiceResponse(Long id, Long reservationId, BigDecimal subtotal, BigDecimal tax,
                           BigDecimal total, InvoiceStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.reservationId = reservationId;
        this.subtotal = subtotal;
        this.tax = tax;
        this.total = total;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getReservationId() { return reservationId; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getTax() { return tax; }
    public BigDecimal getTotal() { return total; }
    public InvoiceStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
