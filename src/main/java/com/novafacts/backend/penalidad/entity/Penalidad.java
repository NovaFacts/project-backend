package com.novafacts.backend.penalidad.entity;

import com.novafacts.backend.auth.entity.User;
import com.novafacts.backend.reservation.entity.Reservation;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "penalidad")
public class Penalidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "reserva_id", nullable = false)
    private Reservation reserva;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = false)
    private User usuario;

    @Column(name = "monto_segun_politica", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoSegunPolitica;

    @Column(name = "monto_aprobado", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoAprobado;

    @Column(name = "monto_condonado", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoCondonado = BigDecimal.ZERO;

    @Column(name = "fecha_cancelacion", nullable = false)
    private LocalDate fechaCancelacion;

    @Column(name = "motivo", columnDefinition = "TEXT")
    private String motivo;

    @Column(name = "calculado_en", nullable = false, updatable = false)
    private LocalDateTime calculadoEn;

    public Penalidad() {}

    @PrePersist
    protected void onCreate() {
        if (calculadoEn == null) calculadoEn = LocalDateTime.now();
        if (montoCondonado == null) montoCondonado = BigDecimal.ZERO;
    }

    public Long getId() { return id; }

    public Reservation getReserva() { return reserva; }
    public void setReserva(Reservation reserva) { this.reserva = reserva; }

    public User getUsuario() { return usuario; }
    public void setUsuario(User usuario) { this.usuario = usuario; }

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

    public LocalDateTime getCalculadoEn() { return calculadoEn; }
}
