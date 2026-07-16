package com.novafacts.backend.factura.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

// RF11/RF12 (Phase 1) + Penalidad aggregation (Phase 2) + subtotal authority (Phase 3) +
// tax authority (Phase 4): descuentoAnticipo, anticipoId, recargoPenalidad, subtotal, and
// impuestos are deliberately absent from this DTO. The client never supplies any of them —
// FacturaService.create() derives descuentoAnticipo from every REGISTRADO anticipo on the
// reservation, recargoPenalidad from its approved Penalidad, subtotal directly from
// Reservation.montoTotal, and impuestos from the configured IVA rate (app.iva-rate) applied
// to (subtotal − descuentoAnticipo + recargoPenalidad) — the same "impuestos = base × 0.19"
// formula documented project-wide (RF_16). A client-sent value for any of these would
// previously have been silently trusted/ignored; removing them outright means Jackson just
// drops any such field from the request body, so there is no input left for a caller to
// manipulate. reservaId and the optional urlDocumento are the only fields left.
public class FacturaRequest {

    @NotNull(message = "El ID de la reserva es obligatorio")
    @Positive(message = "El ID de la reserva debe ser mayor a cero")
    private Long reservaId;

    @Size(max = 500, message = "La URL del documento no puede superar los 500 caracteres")
    private String urlDocumento;

    public FacturaRequest() {}

    public Long getReservaId()                  { return reservaId; }
    public void setReservaId(Long reservaId)    { this.reservaId = reservaId; }

    public String getUrlDocumento()             { return urlDocumento; }
    public void setUrlDocumento(String u)       { this.urlDocumento = u; }
}
