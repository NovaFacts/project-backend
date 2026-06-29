package com.novafacts.backend.devolucion.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class DevolucionRequest {

    @NotNull(message = "El ID de la reserva es obligatorio")
    @Positive(message = "El ID de la reserva debe ser mayor a cero")
    private Long reservaId;

    @NotNull(message = "El ID del anticipo es obligatorio")
    @Positive(message = "El ID del anticipo debe ser mayor a cero")
    private Long anticipoId;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
    @Digits(integer = 10, fraction = 2, message = "El monto debe tener máximo 10 dígitos enteros y 2 decimales")
    private BigDecimal monto;

    @Size(max = 80, message = "El método no puede superar los 80 caracteres")
    private String metodo;

    public DevolucionRequest() {}

    public Long getReservaId()                  { return reservaId; }
    public void setReservaId(Long reservaId)    { this.reservaId = reservaId; }

    public Long getAnticipoId()                 { return anticipoId; }
    public void setAnticipoId(Long anticipoId)  { this.anticipoId = anticipoId; }

    public BigDecimal getMonto()                { return monto; }
    public void setMonto(BigDecimal monto)      { this.monto = monto; }

    public String getMetodo()                   { return metodo; }
    public void setMetodo(String metodo)        { this.metodo = metodo; }
}
