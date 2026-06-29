package com.novafacts.backend.factura.dto;

import com.novafacts.backend.invoice.entity.InvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class FacturaResponse {

    private final Long id;
    private final Long reservaId;
    private final Long usuarioId;
    private final String usuarioNombre;
    private final String numeroFactura;
    private final BigDecimal subtotal;
    private final BigDecimal descuentoAnticipo;
    private final BigDecimal recargoPenalidad;
    private final BigDecimal impuestos;
    private final BigDecimal total;
    private final InvoiceStatus estado;
    private final String urlDocumento;
    private final LocalDateTime emitidaEn;

    public FacturaResponse(Long id, Long reservaId, Long usuarioId, String usuarioNombre,
                           String numeroFactura, BigDecimal subtotal, BigDecimal descuentoAnticipo,
                           BigDecimal recargoPenalidad, BigDecimal impuestos, BigDecimal total,
                           InvoiceStatus estado, String urlDocumento, LocalDateTime emitidaEn) {
        this.id                = id;
        this.reservaId         = reservaId;
        this.usuarioId         = usuarioId;
        this.usuarioNombre     = usuarioNombre;
        this.numeroFactura     = numeroFactura;
        this.subtotal          = subtotal;
        this.descuentoAnticipo = descuentoAnticipo;
        this.recargoPenalidad  = recargoPenalidad;
        this.impuestos         = impuestos;
        this.total             = total;
        this.estado            = estado;
        this.urlDocumento      = urlDocumento;
        this.emitidaEn         = emitidaEn;
    }

    public Long getId()                      { return id; }
    public Long getReservaId()               { return reservaId; }
    public Long getUsuarioId()               { return usuarioId; }
    public String getUsuarioNombre()         { return usuarioNombre; }
    public String getNumeroFactura()         { return numeroFactura; }
    public BigDecimal getSubtotal()          { return subtotal; }
    public BigDecimal getDescuentoAnticipo() { return descuentoAnticipo; }
    public BigDecimal getRecargoPenalidad()  { return recargoPenalidad; }
    public BigDecimal getImpuestos()         { return impuestos; }
    public BigDecimal getTotal()             { return total; }
    public InvoiceStatus getEstado()         { return estado; }
    public String getUrlDocumento()          { return urlDocumento; }
    public LocalDateTime getEmitidaEn()      { return emitidaEn; }
}
