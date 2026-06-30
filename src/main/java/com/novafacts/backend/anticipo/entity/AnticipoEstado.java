package com.novafacts.backend.anticipo.entity;

public enum AnticipoEstado {
    /** Initial state: payment received and recorded, not yet applied to any invoice. */
    REGISTRADO,

    /** Terminal state (invoice path): deducted from a Factura via FacturaService.create(). */
    APLICADO,

    /** Terminal state (refund path): refunded to the guest via DevolucionService.create(). */
    DEVUELTO
}
