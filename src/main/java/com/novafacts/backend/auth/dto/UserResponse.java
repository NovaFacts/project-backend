package com.novafacts.backend.auth.dto;

public class UserResponse {

    private Long id;
    private String username;

    public UserResponse(Long id, String username) {
        this.id = id;
        this.username = username;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }
}
