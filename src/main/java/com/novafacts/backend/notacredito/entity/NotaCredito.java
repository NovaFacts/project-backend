package com.novafacts.backend.notacredito.entity;

import com.novafacts.backend.auth.entity.User;
import com.novafacts.backend.factura.entity.Factura;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "nota_credito")
public class NotaCredito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "factura_id", nullable = false)
    private Factura factura;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = false)
    private User usuario;

    @Column(name = "numero_nota", nullable = false, unique = true, length = 50)
    private String numeroNota;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(name = "motivo")
    private String motivo;

    @Column(name = "emitida_en", nullable = false, updatable = false)
    private LocalDateTime emitidaEn;

    public NotaCredito() {}

    @PrePersist
    protected void onCreate() {
        if (emitidaEn == null) emitidaEn = LocalDateTime.now();
    }

    public Long getId()                          { return id; }

    public Factura getFactura()                  { return factura; }
    public void setFactura(Factura factura)      { this.factura = factura; }

    public User getUsuario()                     { return usuario; }
    public void setUsuario(User usuario)         { this.usuario = usuario; }

    public String getNumeroNota()                { return numeroNota; }
    public void setNumeroNota(String n)          { this.numeroNota = n; }

    public BigDecimal getMonto()                 { return monto; }
    public void setMonto(BigDecimal monto)       { this.monto = monto; }

    public String getMotivo()                    { return motivo; }
    public void setMotivo(String motivo)         { this.motivo = motivo; }

    public LocalDateTime getEmitidaEn()          { return emitidaEn; }
}
