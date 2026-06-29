package com.novafacts.backend.politicacancelacion.dto;

import java.math.BigDecimal;

public class PoliticaCancelacionResponse {

    private Integer id;
    private Long propiedadId;
    private String propiedadNombre;
    private String nombre;
    private String descripcion;
    private BigDecimal porcentajeReembolso;
    private Integer diasAviso;

    public PoliticaCancelacionResponse(Integer id, Long propiedadId, String propiedadNombre,
                                       String nombre, String descripcion,
                                       BigDecimal porcentajeReembolso, Integer diasAviso) {
        this.id = id;
        this.propiedadId = propiedadId;
        this.propiedadNombre = propiedadNombre;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.porcentajeReembolso = porcentajeReembolso;
        this.diasAviso = diasAviso;
    }

    public Integer getId() { return id; }
    public Long getPropiedadId() { return propiedadId; }
    public String getPropiedadNombre() { return propiedadNombre; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public BigDecimal getPorcentajeReembolso() { return porcentajeReembolso; }
    public Integer getDiasAviso() { return diasAviso; }
}
