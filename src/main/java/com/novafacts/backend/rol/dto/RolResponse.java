package com.novafacts.backend.rol.dto;

public class RolResponse {

    private final Integer id;
    private final String nombre;
    private final String descripcion;

    public RolResponse(Integer id, String nombre, String descripcion) {
        this.id = id;
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
}
