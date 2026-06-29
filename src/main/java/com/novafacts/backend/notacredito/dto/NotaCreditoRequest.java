package com.novafacts.backend.notacredito.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class NotaCreditoRequest {

    @NotNull(message = "El ID de la factura es obligatorio")
    @Positive(message = "El ID de la factura debe ser mayor a cero")
    private Long facturaId;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
    @Digits(integer = 10, fraction = 2, message = "El monto debe tener máximo 10 dígitos enteros y 2 decimales")
    private BigDecimal monto;

    private String motivo;

    public NotaCreditoRequest() {}

    public Long getFacturaId()                  { return facturaId; }
    public void setFacturaId(Long facturaId)    { this.facturaId = facturaId; }

    public BigDecimal getMonto()                { return monto; }
    public void setMonto(BigDecimal monto)      { this.monto = monto; }

    public String getMotivo()                   { return motivo; }
    public void setMotivo(String motivo)        { this.motivo = motivo; }
}
