package com.novafacts.backend.temporada.dto;

import java.time.LocalDate;

public class TemporadaResponse {

    private final Integer id;
    private final String nombre;
    private final LocalDate fechaInicio;
    private final LocalDate fechaFin;

    public TemporadaResponse(Integer id, String nombre, LocalDate fechaInicio, LocalDate fechaFin) {
        this.id = id;
        this.nombre = nombre;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
    }

    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public LocalDate getFechaFin() { return fechaFin; }
}
