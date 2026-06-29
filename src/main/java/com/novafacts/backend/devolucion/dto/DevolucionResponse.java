package com.novafacts.backend.devolucion.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class DevolucionResponse {

    private final Long id;
    private final Long reservaId;
    private final Long anticipoId;
    private final Long usuarioId;
    private final String usuarioNombre;
    private final BigDecimal monto;
    private final String metodo;
    private final String estado;
    private final LocalDateTime generadaEn;
    private final LocalDateTime procesadaEn;

    public DevolucionResponse(Long id, Long reservaId, Long anticipoId,
                              Long usuarioId, String usuarioNombre,
                              BigDecimal monto, String metodo, String estado,
                              LocalDateTime generadaEn, LocalDateTime procesadaEn) {
        this.id            = id;
        this.reservaId     = reservaId;
        this.anticipoId    = anticipoId;
        this.usuarioId     = usuarioId;
        this.usuarioNombre = usuarioNombre;
        this.monto         = monto;
        this.metodo        = metodo;
        this.estado        = estado;
        this.generadaEn    = generadaEn;
        this.procesadaEn   = procesadaEn;
    }

    public Long getId()                          { return id; }
    public Long getReservaId()                   { return reservaId; }
    public Long getAnticipoId()                  { return anticipoId; }
    public Long getUsuarioId()                   { return usuarioId; }
    public String getUsuarioNombre()             { return usuarioNombre; }
    public BigDecimal getMonto()                 { return monto; }
    public String getMetodo()                    { return metodo; }
    public String getEstado()                    { return estado; }
    public LocalDateTime getGeneradaEn()         { return generadaEn; }
    public LocalDateTime getProcesadaEn()        { return procesadaEn; }
}
