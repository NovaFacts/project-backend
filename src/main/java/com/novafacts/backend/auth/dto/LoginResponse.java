package com.novafacts.backend.auth.dto;

public class LoginResponse {

    private String message;

    public LoginResponse(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
