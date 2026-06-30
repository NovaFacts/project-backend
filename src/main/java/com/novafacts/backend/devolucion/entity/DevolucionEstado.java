package com.novafacts.backend.devolucion.entity;

public enum DevolucionEstado {
    /** Initial state: refund requested and queued for processing. */
    PENDIENTE,

    /** Terminal state (success): refund confirmed and funds disbursed. */
    PROCESADA,

    /** Terminal state (denied): refund request was rejected. */
    RECHAZADA
}
