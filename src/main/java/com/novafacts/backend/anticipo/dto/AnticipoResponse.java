package com.novafacts.backend.anticipo.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class AnticipoResponse {

    private final Long id;
    private final Long reservaId;
    private final Long usuarioId;
    private final String usuarioNombre;
    private final BigDecimal monto;
    private final LocalDate fechaPago;
    private final String metodoPago;
    private final String estado;
    private final LocalDateTime registradoEn;

    public AnticipoResponse(Long id, Long reservaId, Long usuarioId, String usuarioNombre,
                             BigDecimal monto, LocalDate fechaPago, String metodoPago,
                             String estado, LocalDateTime registradoEn) {
        this.id = id;
        this.reservaId = reservaId;
        this.usuarioId = usuarioId;
        this.usuarioNombre = usuarioNombre;
        this.monto = monto;
        this.fechaPago = fechaPago;
        this.metodoPago = metodoPago;
        this.estado = estado;
        this.registradoEn = registradoEn;
    }

    public Long getId() { return id; }
    public Long getReservaId() { return reservaId; }
    public Long getUsuarioId() { return usuarioId; }
    public String getUsuarioNombre() { return usuarioNombre; }
    public BigDecimal getMonto() { return monto; }
    public LocalDate getFechaPago() { return fechaPago; }
    public String getMetodoPago() { return metodoPago; }
    public String getEstado() { return estado; }
    public LocalDateTime getRegistradoEn() { return registradoEn; }
}
