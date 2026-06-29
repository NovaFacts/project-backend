package com.novafacts.backend.politicacancelacion.entity;

import com.novafacts.backend.property.entity.Property;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "politica_cancelacion")
public class PoliticaCancelacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "propiedad_id", nullable = false)
    private Property propiedad;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "porcentaje_reembolso", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeReembolso;

    @Column(name = "dias_aviso", nullable = false)
    private Integer diasAviso;

    public PoliticaCancelacion() {}

    public Integer getId() { return id; }

    public Property getPropiedad() { return propiedad; }
    public void setPropiedad(Property propiedad) { this.propiedad = propiedad; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public BigDecimal getPorcentajeReembolso() { return porcentajeReembolso; }
    public void setPorcentajeReembolso(BigDecimal porcentajeReembolso) { this.porcentajeReembolso = porcentajeReembolso; }

    public Integer getDiasAviso() { return diasAviso; }
    public void setDiasAviso(Integer diasAviso) { this.diasAviso = diasAviso; }
}
