package com.novafacts.backend.auth.controller;

import com.novafacts.backend.auth.dto.LoginRequest;
import com.novafacts.backend.auth.dto.LoginResponse;
import com.novafacts.backend.auth.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request
    ) {
        return userService.login(request);
    }
}
