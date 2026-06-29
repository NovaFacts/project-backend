package com.novafacts.backend.property.dto;

public class PropertyResponse {

    private final Long id;
    private final String name;
    private final String address;
    private final String descripcion;
    private final Boolean activa;

    public PropertyResponse(Long id, String name, String address, String descripcion, Boolean activa) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.descripcion = descripcion;
        this.activa = activa;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getDescripcion() { return descripcion; }
    public Boolean getActiva() { return activa; }
}
