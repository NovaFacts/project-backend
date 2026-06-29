package com.novafacts.backend.notacredito.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class NotaCreditoResponse {

    private final Long id;
    private final Long facturaId;
    private final String numeroFactura;
    private final Long usuarioId;
    private final String usuarioNombre;
    private final String numeroNota;
    private final BigDecimal monto;
    private final String motivo;
    private final LocalDateTime emitidaEn;

    public NotaCreditoResponse(Long id, Long facturaId, String numeroFactura,
                               Long usuarioId, String usuarioNombre, String numeroNota,
                               BigDecimal monto, String motivo, LocalDateTime emitidaEn) {
        this.id            = id;
        this.facturaId     = facturaId;
        this.numeroFactura = numeroFactura;
        this.usuarioId     = usuarioId;
        this.usuarioNombre = usuarioNombre;
        this.numeroNota    = numeroNota;
        this.monto         = monto;
        this.motivo        = motivo;
        this.emitidaEn     = emitidaEn;
    }

    public Long getId()              { return id; }
    public Long getFacturaId()       { return facturaId; }
    public String getNumeroFactura() { return numeroFactura; }
    public Long getUsuarioId()       { return usuarioId; }
    public String getUsuarioNombre() { return usuarioNombre; }
    public String getNumeroNota()    { return numeroNota; }
    public BigDecimal getMonto()     { return monto; }
    public String getMotivo()        { return motivo; }
    public LocalDateTime getEmitidaEn() { return emitidaEn; }
}
