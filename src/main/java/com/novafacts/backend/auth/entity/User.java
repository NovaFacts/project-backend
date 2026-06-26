package com.novafacts.backend.auth.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuario")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Java field 'username' maps to 'email' column — email is the unique login identifier in usuario
    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String username;

    // Java field 'password' maps to 'password_hash' column
    @Column(name = "password_hash", nullable = false, length = 255)
    private String password;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "rol_id", nullable = false)
    private Integer rolId;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    public User() {
    }

    public User(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @PrePersist
    protected void onCreate() {
        if (creadoEn == null) creadoEn = LocalDateTime.now();
        if (activo == null) activo = true;
    }

    public Long getId() { return id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public Integer getRolId() { return rolId; }
    public void setRolId(Integer rolId) { this.rolId = rolId; }

    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }

    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
}
