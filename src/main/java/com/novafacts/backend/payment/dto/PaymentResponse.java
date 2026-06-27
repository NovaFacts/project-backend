package com.novafacts.backend.payment.dto;

import com.novafacts.backend.payment.entity.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentResponse {

    private Long id;
    private Long invoiceId;
    private BigDecimal amount;
    private PaymentMethod paymentMethod;
    private String reference;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;

    public PaymentResponse(Long id, Long invoiceId, BigDecimal amount, PaymentMethod paymentMethod,
                           String reference, LocalDateTime paidAt, LocalDateTime createdAt) {
        this.id = id;
        this.invoiceId = invoiceId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.reference = reference;
        this.paidAt = paidAt;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getInvoiceId() { return invoiceId; }
    public BigDecimal getAmount() { return amount; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public String getReference() { return reference; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
