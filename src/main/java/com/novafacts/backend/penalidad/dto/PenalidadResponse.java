package com.novafacts.backend.penalidad.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class PenalidadResponse {

    private final Long id;
    private final Long reservaId;
    private final Long usuarioId;
    private final String usuarioNombre;
    private final BigDecimal montoSegunPolitica;
    private final BigDecimal montoAprobado;
    private final BigDecimal montoCondonado;
    private final LocalDate fechaCancelacion;
    private final String motivo;
    private final LocalDateTime calculadoEn;

    public PenalidadResponse(Long id, Long reservaId, Long usuarioId, String usuarioNombre,
                              BigDecimal montoSegunPolitica, BigDecimal montoAprobado,
                              BigDecimal montoCondonado, LocalDate fechaCancelacion,
                              String motivo, LocalDateTime calculadoEn) {
        this.id = id;
        this.reservaId = reservaId;
        this.usuarioId = usuarioId;
        this.usuarioNombre = usuarioNombre;
        this.montoSegunPolitica = montoSegunPolitica;
        this.montoAprobado = montoAprobado;
        this.montoCondonado = montoCondonado;
        this.fechaCancelacion = fechaCancelacion;
        this.motivo = motivo;
        this.calculadoEn = calculadoEn;
    }

    public Long getId() { return id; }
    public Long getReservaId() { return reservaId; }
    public Long getUsuarioId() { return usuarioId; }
    public String getUsuarioNombre() { return usuarioNombre; }
    public BigDecimal getMontoSegunPolitica() { return montoSegunPolitica; }
    public BigDecimal getMontoAprobado() { return montoAprobado; }
    public BigDecimal getMontoCondonado() { return montoCondonado; }
    public LocalDate getFechaCancelacion() { return fechaCancelacion; }
    public String getMotivo() { return motivo; }
    public LocalDateTime getCalculadoEn() { return calculadoEn; }
}
