package com.novafacts.backend.payment.dto;

import com.novafacts.backend.payment.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class CreatePaymentRequest {

    @NotNull(message = "El identificador de la factura es obligatorio")
    @Positive(message = "El identificador de la factura debe ser mayor a cero")
    private Long invoiceId;

    @NotNull(message = "El método de pago es obligatorio")
    private PaymentMethod paymentMethod;

    @Size(max = 100, message = "La referencia no puede superar 100 caracteres")
    private String reference;

    public Long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Long invoiceId) { this.invoiceId = invoiceId; }

    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
}
