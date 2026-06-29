package com.novafacts.backend.penalidad.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public class PenalidadRequest {

    @NotNull(message = "El identificador de la reserva es obligatorio")
    @Positive(message = "El identificador de la reserva debe ser mayor a cero")
    private Long reservaId;

    @NotNull(message = "El monto según política es obligatorio")
    @Positive(message = "El monto según política debe ser mayor a cero")
    @Digits(integer = 10, fraction = 2, message = "El monto según política no es válido")
    private BigDecimal montoSegunPolitica;

    @NotNull(message = "El monto aprobado es obligatorio")
    @PositiveOrZero(message = "El monto aprobado no puede ser negativo")
    @Digits(integer = 10, fraction = 2, message = "El monto aprobado no es válido")
    private BigDecimal montoAprobado;

    @PositiveOrZero(message = "El monto condonado no puede ser negativo")
    @Digits(integer = 10, fraction = 2, message = "El monto condonado no es válido")
    private BigDecimal montoCondonado;

    @NotNull(message = "La fecha de cancelación es obligatoria")
    private LocalDate fechaCancelacion;

    private String motivo;

    public Long getReservaId() { return reservaId; }
    public void setReservaId(Long reservaId) { this.reservaId = reservaId; }

    public BigDecimal getMontoSegunPolitica() { return montoSegunPolitica; }
    public void setMontoSegunPolitica(BigDecimal montoSegunPolitica) { this.montoSegunPolitica = montoSegunPolitica; }

    public BigDecimal getMontoAprobado() { return montoAprobado; }
    public void setMontoAprobado(BigDecimal montoAprobado) { this.montoAprobado = montoAprobado; }

    public BigDecimal getMontoCondonado() { return montoCondonado; }
    public void setMontoCondonado(BigDecimal montoCondonado) { this.montoCondonado = montoCondonado; }

    public LocalDate getFechaCancelacion() { return fechaCancelacion; }
    public void setFechaCancelacion(LocalDate fechaCancelacion) { this.fechaCancelacion = fechaCancelacion; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
}
