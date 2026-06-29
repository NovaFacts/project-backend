package com.novafacts.backend.auth.dto;

import com.novafacts.backend.rol.dto.RolResponse;

public class UserResponse {

    private final Long id;
    private final String email;
    private final String nombre;
    private final RolResponse rol;

    public UserResponse(Long id, String email, String nombre, RolResponse rol) {
        this.id = id;
        this.email = email;
        this.nombre = nombre;
        this.rol = rol;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getNombre() { return nombre; }
    public RolResponse getRol() { return rol; }
}
