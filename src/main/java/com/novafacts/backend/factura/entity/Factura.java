package com.novafacts.backend.factura.entity;

import com.novafacts.backend.auth.entity.User;
import com.novafacts.backend.invoice.entity.InvoiceStatus;
import com.novafacts.backend.reservation.entity.Reservation;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "factura")
public class Factura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reserva_id", nullable = false)
    private Reservation reserva;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private User usuario;

    @Column(name = "numero_factura", nullable = false, unique = true, length = 50)
    private String numeroFactura;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "descuento_anticipo", nullable = false, precision = 12, scale = 2)
    private BigDecimal descuentoAnticipo;

    @Column(name = "recargo_penalidad", nullable = false, precision = 12, scale = 2)
    private BigDecimal recargoPenalidad;

    @Column(name = "impuestos", nullable = false, precision = 12, scale = 2)
    private BigDecimal impuestos;

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 50)
    private InvoiceStatus estado;

    @Column(name = "url_documento")
    private String urlDocumento;

    @Column(name = "emitida_en", nullable = false, updatable = false)
    private LocalDateTime emitidaEn;

    public Factura() {}

    @PrePersist
    protected void onCreate() {
        if (emitidaEn == null)          emitidaEn          = LocalDateTime.now();
        if (descuentoAnticipo == null)  descuentoAnticipo  = BigDecimal.ZERO;
        if (recargoPenalidad == null)   recargoPenalidad   = BigDecimal.ZERO;
        if (impuestos == null)          impuestos          = BigDecimal.ZERO;
    }

    public Long getId()                              { return id; }

    public Reservation getReserva()                  { return reserva; }
    public void setReserva(Reservation reserva)      { this.reserva = reserva; }

    public User getUsuario()                         { return usuario; }
    public void setUsuario(User usuario)             { this.usuario = usuario; }

    public String getNumeroFactura()                 { return numeroFactura; }
    public void setNumeroFactura(String n)           { this.numeroFactura = n; }

    public BigDecimal getSubtotal()                  { return subtotal; }
    public void setSubtotal(BigDecimal subtotal)     { this.subtotal = subtotal; }

    public BigDecimal getDescuentoAnticipo()         { return descuentoAnticipo; }
    public void setDescuentoAnticipo(BigDecimal d)   { this.descuentoAnticipo = d; }

    public BigDecimal getRecargoPenalidad()          { return recargoPenalidad; }
    public void setRecargoPenalidad(BigDecimal r)    { this.recargoPenalidad = r; }

    public BigDecimal getImpuestos()                 { return impuestos; }
    public void setImpuestos(BigDecimal impuestos)   { this.impuestos = impuestos; }

    public BigDecimal getTotal()                     { return total; }
    public void setTotal(BigDecimal total)           { this.total = total; }

    public InvoiceStatus getEstado()                 { return estado; }
    public void setEstado(InvoiceStatus estado)      { this.estado = estado; }

    public String getUrlDocumento()                  { return urlDocumento; }
    public void setUrlDocumento(String urlDocumento) { this.urlDocumento = urlDocumento; }

    public LocalDateTime getEmitidaEn()              { return emitidaEn; }
}
