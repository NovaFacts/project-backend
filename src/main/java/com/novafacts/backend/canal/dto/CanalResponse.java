package com.novafacts.backend.canal.dto;

public class CanalResponse {

    private final Integer id;
    private final String nombre;
    private final String tipo;

    public CanalResponse(Integer id, String nombre, String tipo) {
        this.id = id;
        this.nombre = nombre;
        this.tipo = tipo;
    }

    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
    public String getTipo() { return tipo; }
}
