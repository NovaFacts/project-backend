package com.novafacts.backend.auth.controller;

import com.novafacts.backend.auth.dto.CreateUserRequest;
import com.novafacts.backend.auth.dto.UserResponse;
import com.novafacts.backend.auth.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "http://localhost:5173")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public UserResponse createUser(
            @RequestBody CreateUserRequest request
    ) {
        return userService.createUser(request);
    }

    @GetMapping
    public List<UserResponse> getUsers() {
        return userService.getUsers();
    }
    
    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable Long id) {
    userService.deleteUser(id);
}
}
