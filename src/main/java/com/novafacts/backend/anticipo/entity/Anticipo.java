package com.novafacts.backend.anticipo.entity;

import com.novafacts.backend.auth.entity.User;
import com.novafacts.backend.reservation.entity.Reservation;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "anticipo")
public class Anticipo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "reserva_id", nullable = false)
    private Reservation reserva;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = false)
    private User usuario;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(name = "fecha_pago", nullable = false)
    private LocalDate fechaPago;

    @Column(name = "metodo_pago", length = 80)
    private String metodoPago;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 50)
    private AnticipoEstado estado;

    @Column(name = "registrado_en", nullable = false, updatable = false)
    private LocalDateTime registradoEn;

    public Anticipo() {}

    @PrePersist
    protected void onCreate() {
        if (registradoEn == null) registradoEn = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public Reservation getReserva() { return reserva; }
    public void setReserva(Reservation reserva) { this.reserva = reserva; }

    public User getUsuario() { return usuario; }
    public void setUsuario(User usuario) { this.usuario = usuario; }

    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }

    public LocalDate getFechaPago() { return fechaPago; }
    public void setFechaPago(LocalDate fechaPago) { this.fechaPago = fechaPago; }

    public String getMetodoPago() { return metodoPago; }
    public void setMetodoPago(String metodoPago) { this.metodoPago = metodoPago; }

    public AnticipoEstado getEstado() { return estado; }
    public void setEstado(AnticipoEstado estado) { this.estado = estado; }

    public LocalDateTime getRegistradoEn() { return registradoEn; }
}
