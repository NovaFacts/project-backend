package com.novafacts.backend.auth.service;

import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.novafacts.backend.auth.dto.CreateUserRequest;
import com.novafacts.backend.auth.dto.LoginRequest;
import com.novafacts.backend.auth.dto.LoginResponse;
import com.novafacts.backend.auth.dto.UserResponse;
import com.novafacts.backend.auth.entity.User;
import com.novafacts.backend.auth.jwt.JwtService;
import com.novafacts.backend.auth.repository.UserRepository;
import com.novafacts.backend.common.PageResponse;
import com.novafacts.backend.rol.dto.RolResponse;
import com.novafacts.backend.rol.entity.Rol;
import com.novafacts.backend.rol.repository.RolRepository;

@Service
public class UserService {

    /**
     * LOW-17: A valid BCrypt hash used solely for timing equalization when a login
     * request is made for a non-existent email.  By calling passwordEncoder.matches()
     * against this dummy hash, the "user not found" path takes the same ~100ms as the
     * "user found, wrong password" path — preventing an attacker from enumerating
     * registered emails by comparing response times.
     */
    private static final String DUMMY_HASH =
            "$2b$10$M4yBMutDHHxpjiCDu6tFmeCXqplQzntLzsK5SsaizqyMIUE3oHCPi";

    private final UserRepository userRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserService(
            UserRepository userRepository,
            RolRepository rolRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository  = userRepository;
        this.rolRepository   = rolRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService      = jwtService;
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Usuario no encontrado"));
        String authenticatedUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        if (authenticatedUsername.equals(user.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No puedes desactivar tu propia cuenta de administrador");
        }
        user.setActivo(false);
        userRepository.save(user);
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El correo electrónico ya está registrado");
        }

        Rol rol = rolRepository.findById(request.getRolId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Rol no encontrado"));

        User user = new User();
        user.setUsername(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNombre(request.getNombre());
        user.setRol(rol);
        user.setActivo(true);

        return toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getUsers(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("nombre").ascending());
        // MEDIUM-16: exclude soft-deleted users from the management listing
        return new PageResponse<>(userRepository.findByActivoTrue(pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Optional<User> userOpt = userRepository.findByUsername(request.getEmail());

        if (userOpt.isEmpty()) {
            // LOW-17: timing equalization — run a dummy BCrypt comparison so that
            // the "email not found" path takes the same ~100ms as the "wrong password"
            // path. Without this, an attacker can enumerate registered emails by
            // observing that non-existent-user responses arrive faster.
            passwordEncoder.matches(request.getPassword(), DUMMY_HASH);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
        }

        User user = userOpt.get();

        if (!Boolean.TRUE.equals(user.getActivo())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
        }

        String token = jwtService.generateToken(user.getUsername(), user.getRol().getNombre());
        return new LoginResponse(token, user.getRol().getNombre(), user.getNombre());
    }

    public UserResponse toResponse(User user) {
        RolResponse rolResponse = new RolResponse(
                user.getRol().getId(),
                user.getRol().getNombre(),
                user.getRol().getDescripcion()
        );
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getNombre(),
                Boolean.TRUE.equals(user.getActivo()),
                rolResponse
        );
    }
}
