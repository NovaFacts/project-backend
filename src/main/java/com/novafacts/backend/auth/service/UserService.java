package com.novafacts.backend.auth.service;

import com.novafacts.backend.auth.dto.LoginRequest;
import com.novafacts.backend.auth.dto.LoginResponse;

import com.novafacts.backend.auth.dto.CreateUserRequest;
import com.novafacts.backend.auth.dto.UserResponse;
import com.novafacts.backend.auth.entity.User;
import com.novafacts.backend.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }
    
    public void deleteUser(Long id) {
    userRepository.deleteById(id);
    }

    public UserResponse createUser(CreateUserRequest request) {

        User user = new User();

        user.setUsername(request.getUsername());

        user.setPassword(
                passwordEncoder.encode(request.getPassword())
        );

        User savedUser = userRepository.save(user);

        return new UserResponse(
                savedUser.getId(),
                savedUser.getUsername()
        );
    }

    public List<UserResponse> getUsers() {

        return userRepository.findAll()
                .stream()
                .map(user -> new UserResponse(
                        user.getId(),
                        user.getUsername()
                ))
                .toList();
    }
    
    public LoginResponse login(LoginRequest request) {

    User user = userRepository.findByUsername(request.getUsername())
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

    boolean passwordMatches = passwordEncoder.matches(
            request.getPassword(),
            user.getPassword()
    );

    if (!passwordMatches) {
        throw new RuntimeException("Contraseña incorrecta");
    }

    return new LoginResponse("Login exitoso");
    }
}
