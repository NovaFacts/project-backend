package com.novafacts.backend.auth.dto;

public class LoginResponse {

    private final String token;
    private final String rol;
    private final String nombre;

    public LoginResponse(String token, String rol, String nombre) {
        this.token = token;
        this.rol = rol;
        this.nombre = nombre;
    }

    public String getToken() { return token; }
    public String getRol() { return rol; }
    public String getNombre() { return nombre; }
}
