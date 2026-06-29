package com.novafacts.backend.reservation.dto;

import com.novafacts.backend.reservation.entity.ReservationStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ReservationResponse {

    private Long id;
    private Long propertyId;
    private Integer canalId;
    private String canalNombre;
    private Integer temporadaId;
    private String temporadaNombre;
    private Integer politicaCancelacionId;
    private String politicaCancelacionNombre;
    private Long usuarioCreadorId;
    private String usuarioCreadorNombre;
    private String clienteNombre;
    private String clienteEmail;
    private String clienteTelefono;
    private BigDecimal montoTotal;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private Integer guestCount;
    private ReservationStatus status;
    private LocalDateTime createdAt;

    public ReservationResponse(Long id, Long propertyId,
                               Integer canalId, String canalNombre,
                               Integer temporadaId, String temporadaNombre,
                               Integer politicaCancelacionId, String politicaCancelacionNombre,
                               Long usuarioCreadorId, String usuarioCreadorNombre,
                               String clienteNombre, String clienteEmail, String clienteTelefono,
                               BigDecimal montoTotal,
                               LocalDate checkIn, LocalDate checkOut, Integer guestCount,
                               ReservationStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.propertyId = propertyId;
        this.canalId = canalId;
        this.canalNombre = canalNombre;
        this.temporadaId = temporadaId;
        this.temporadaNombre = temporadaNombre;
        this.politicaCancelacionId = politicaCancelacionId;
        this.politicaCancelacionNombre = politicaCancelacionNombre;
        this.usuarioCreadorId = usuarioCreadorId;
        this.usuarioCreadorNombre = usuarioCreadorNombre;
        this.clienteNombre = clienteNombre;
        this.clienteEmail = clienteEmail;
        this.clienteTelefono = clienteTelefono;
        this.montoTotal = montoTotal;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.guestCount = guestCount;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getPropertyId() { return propertyId; }
    public Integer getCanalId() { return canalId; }
    public String getCanalNombre() { return canalNombre; }
    public Integer getTemporadaId() { return temporadaId; }
    public String getTemporadaNombre() { return temporadaNombre; }
    public Integer getPoliticaCancelacionId() { return politicaCancelacionId; }
    public String getPoliticaCancelacionNombre() { return politicaCancelacionNombre; }
    public Long getUsuarioCreadorId() { return usuarioCreadorId; }
    public String getUsuarioCreadorNombre() { return usuarioCreadorNombre; }
    public String getClienteNombre() { return clienteNombre; }
    public String getClienteEmail() { return clienteEmail; }
    public String getClienteTelefono() { return clienteTelefono; }
    public BigDecimal getMontoTotal() { return montoTotal; }
    public LocalDate getCheckIn() { return checkIn; }
    public LocalDate getCheckOut() { return checkOut; }
    public Integer getGuestCount() { return guestCount; }
    public ReservationStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
