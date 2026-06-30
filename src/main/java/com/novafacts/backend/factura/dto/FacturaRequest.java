package com.novafacts.backend.factura.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class FacturaRequest {

    @NotNull(message = "El ID de la reserva es obligatorio")
    @Positive(message = "El ID de la reserva debe ser mayor a cero")
    private Long reservaId;

    // Optional: when descuentoAnticipo > 0, provide the anticipoId so the service
    // can atomically mark that anticipo as "aplicado" and prevent a double refund.
    @Positive(message = "El ID del anticipo debe ser mayor a cero")
    private Long anticipoId;

    @NotNull(message = "El subtotal es obligatorio")
    @DecimalMin(value = "0.01", message = "El subtotal debe ser mayor a cero")
    @Digits(integer = 10, fraction = 2, message = "El subtotal debe tener máximo 10 dígitos enteros y 2 decimales")
    private BigDecimal subtotal;

    @PositiveOrZero(message = "El descuento por anticipo no puede ser negativo")
    @Digits(integer = 10, fraction = 2, message = "El descuento por anticipo debe tener máximo 10 dígitos enteros y 2 decimales")
    private BigDecimal descuentoAnticipo;

    @PositiveOrZero(message = "El recargo por penalidad no puede ser negativo")
    @Digits(integer = 10, fraction = 2, message = "El recargo por penalidad debe tener máximo 10 dígitos enteros y 2 decimales")
    private BigDecimal recargoPenalidad;

    @PositiveOrZero(message = "Los impuestos no pueden ser negativos")
    @Digits(integer = 10, fraction = 2, message = "Los impuestos deben tener máximo 10 dígitos enteros y 2 decimales")
    private BigDecimal impuestos;

    @Size(max = 500, message = "La URL del documento no puede superar los 500 caracteres")
    private String urlDocumento;

    public FacturaRequest() {}

    public Long getReservaId()                  { return reservaId; }
    public void setReservaId(Long reservaId)    { this.reservaId = reservaId; }

    public BigDecimal getSubtotal()             { return subtotal; }
    public void setSubtotal(BigDecimal s)       { this.subtotal = s; }

    public BigDecimal getDescuentoAnticipo()    { return descuentoAnticipo; }
    public void setDescuentoAnticipo(BigDecimal d) { this.descuentoAnticipo = d; }

    public BigDecimal getRecargoPenalidad()     { return recargoPenalidad; }
    public void setRecargoPenalidad(BigDecimal r) { this.recargoPenalidad = r; }

    public BigDecimal getImpuestos()            { return impuestos; }
    public void setImpuestos(BigDecimal i)      { this.impuestos = i; }

    public String getUrlDocumento()             { return urlDocumento; }
    public void setUrlDocumento(String u)       { this.urlDocumento = u; }

    public Long getAnticipoId()                 { return anticipoId; }
    public void setAnticipoId(Long anticipoId)  { this.anticipoId = anticipoId; }
}
