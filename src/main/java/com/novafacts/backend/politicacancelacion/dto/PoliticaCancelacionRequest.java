package com.novafacts.backend.politicacancelacion.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class PoliticaCancelacionRequest {

    @NotNull(message = "La propiedad es obligatoria")
    private Long propiedadId;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
    private String nombre;

    private String descripcion;

    @NotNull(message = "El porcentaje de reembolso es obligatorio")
    @DecimalMin(value = "0.00", inclusive = true, message = "El porcentaje de reembolso no puede ser negativo")
    @DecimalMax(value = "100.00", inclusive = true, message = "El porcentaje de reembolso no puede superar 100")
    private BigDecimal porcentajeReembolso;

    @NotNull(message = "Los días de aviso son obligatorios")
    @Min(value = 0, message = "Los días de aviso no pueden ser negativos")
    private Integer diasAviso;

    public PoliticaCancelacionRequest() {}

    public Long getPropiedadId() { return propiedadId; }
    public void setPropiedadId(Long propiedadId) { this.propiedadId = propiedadId; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public BigDecimal getPorcentajeReembolso() { return porcentajeReembolso; }
    public void setPorcentajeReembolso(BigDecimal porcentajeReembolso) { this.porcentajeReembolso = porcentajeReembolso; }

    public Integer getDiasAviso() { return diasAviso; }
    public void setDiasAviso(Integer diasAviso) { this.diasAviso = diasAviso; }
}
