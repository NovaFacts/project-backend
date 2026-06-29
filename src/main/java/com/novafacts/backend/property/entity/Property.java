package com.novafacts.backend.property.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "propiedad")
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", nullable = false, length = 150)
    private String name;

    @Column(name = "direccion")
    private String address;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "activa", nullable = false)
    private Boolean activa = true;

    public Property() {}

    public Long getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public Boolean getActiva() { return activa; }
    public void setActiva(Boolean activa) { this.activa = activa; }
}
