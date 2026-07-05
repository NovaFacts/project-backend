package com.novafacts.backend.reservation.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public class CreateReservationRequest {

    @NotNull(message = "El canal es obligatorio")
    private Integer canalId;

    @NotNull(message = "La temporada es obligatoria")
    private Integer temporadaId;

    @NotNull(message = "La política de cancelación es obligatoria")
    private Integer politicaCancelacionId;

    @NotNull(message = "La propiedad es obligatoria")
    @Positive(message = "El identificador de la propiedad debe ser mayor a cero")
    private Long propertyId;

    @NotBlank(message = "El nombre del cliente es obligatorio")
    @Size(max = 150, message = "El nombre del cliente no puede superar 150 caracteres")
    private String clienteNombre;

    @Email(message = "El formato del email del cliente no es válido")
    @Size(max = 150, message = "El email del cliente no puede superar 150 caracteres")
    private String clienteEmail;

    @Size(max = 50, message = "El teléfono del cliente no puede superar 50 caracteres")
    private String clienteTelefono;

    @NotNull(message = "El monto total es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto total debe ser mayor a cero")
    private BigDecimal montoTotal;

    @NotNull(message = "La fecha de inicio es obligatoria")
    private LocalDate checkIn;

    @NotNull(message = "La fecha de fin es obligatoria")
    private LocalDate checkOut;

    @NotNull(message = "La cantidad de huéspedes es obligatoria")
    @Positive(message = "La cantidad de huéspedes debe ser mayor a cero")
    private Integer guestCount;

    public CreateReservationRequest() {}

    public Integer getCanalId() { return canalId; }
    public void setCanalId(Integer canalId) { this.canalId = canalId; }

    public Integer getTemporadaId() { return temporadaId; }
    public void setTemporadaId(Integer temporadaId) { this.temporadaId = temporadaId; }

    public Integer getPoliticaCancelacionId() { return politicaCancelacionId; }
    public void setPoliticaCancelacionId(Integer politicaCancelacionId) {
        this.politicaCancelacionId = politicaCancelacionId;
    }

    public Long getPropertyId() { return propertyId; }
    public void setPropertyId(Long propertyId) { this.propertyId = propertyId; }

    public String getClienteNombre() { return clienteNombre; }
    public void setClienteNombre(String clienteNombre) { this.clienteNombre = clienteNombre; }

    public String getClienteEmail() { return clienteEmail; }
    public void setClienteEmail(String clienteEmail) { this.clienteEmail = clienteEmail; }

    public String getClienteTelefono() { return clienteTelefono; }
    public void setClienteTelefono(String clienteTelefono) { this.clienteTelefono = clienteTelefono; }

    public BigDecimal getMontoTotal() { return montoTotal; }
    public void setMontoTotal(BigDecimal montoTotal) { this.montoTotal = montoTotal; }

    public LocalDate getCheckIn() { return checkIn; }
    public void setCheckIn(LocalDate checkIn) { this.checkIn = checkIn; }

    public LocalDate getCheckOut() { return checkOut; }
    public void setCheckOut(LocalDate checkOut) { this.checkOut = checkOut; }

    public Integer getGuestCount() { return guestCount; }
    public void setGuestCount(Integer guestCount) { this.guestCount = guestCount; }
}
