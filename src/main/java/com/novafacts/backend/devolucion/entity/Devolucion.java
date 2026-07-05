package com.novafacts.backend.devolucion.entity;

import com.novafacts.backend.anticipo.entity.Anticipo;
import com.novafacts.backend.auth.entity.User;
import com.novafacts.backend.reservation.entity.Reservation;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "devolucion")
public class Devolucion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reserva_id", nullable = false)
    private Reservation reserva;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anticipo_id", nullable = false)
    private Anticipo anticipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private User usuario;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(name = "metodo", length = 80)
    private String metodo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 50)
    private DevolucionEstado estado;

    @Column(name = "generada_en", nullable = false, updatable = false)
    private LocalDateTime generadaEn;

    @Column(name = "procesada_en")
    private LocalDateTime procesadaEn;

    public Devolucion() {}

    @PrePersist
    protected void onCreate() {
        if (generadaEn == null) generadaEn = LocalDateTime.now();
    }

    public Long getId()                                { return id; }

    public Reservation getReserva()                    { return reserva; }
    public void setReserva(Reservation reserva)        { this.reserva = reserva; }

    public Anticipo getAnticipo()                      { return anticipo; }
    public void setAnticipo(Anticipo anticipo)         { this.anticipo = anticipo; }

    public User getUsuario()                           { return usuario; }
    public void setUsuario(User usuario)               { this.usuario = usuario; }

    public BigDecimal getMonto()                       { return monto; }
    public void setMonto(BigDecimal monto)             { this.monto = monto; }

    public String getMetodo()                          { return metodo; }
    public void setMetodo(String metodo)               { this.metodo = metodo; }

    public DevolucionEstado getEstado()                { return estado; }
    public void setEstado(DevolucionEstado estado)     { this.estado = estado; }

    public LocalDateTime getGeneradaEn()               { return generadaEn; }

    public LocalDateTime getProcesadaEn()              { return procesadaEn; }
    public void setProcesadaEn(LocalDateTime t)        { this.procesadaEn = t; }
}
