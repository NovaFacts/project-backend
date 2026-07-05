package com.novafacts.backend.auth;

import com.novafacts.backend.auth.entity.User;
import com.novafacts.backend.auth.repository.UserRepository;
import com.novafacts.backend.rol.entity.Rol;
import com.novafacts.backend.rol.repository.RolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The rate limiter's bucket map is a singleton field on a Spring-managed bean, shared
 * across every test method that reuses this application context — so these assertions
 * are written as ONE ordered sequence (not independent @Test methods) to avoid depending
 * on cross-method execution order or leaking rate-limit state between unrelated tests.
 * Capacity is overridden low (3) via @TestPropertySource so the limit can be reached
 * deterministically and quickly, without waiting anywhere near the 60s production refill.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "security.rate-limit.login.capacity=3",
        "security.rate-limit.login.refill-tokens=3",
        "security.rate-limit.login.refill-duration-seconds=60"
})
class LoginRateLimitFilterTest {

    private static final String LOGIN_JSON = "{\"email\":\"%s\",\"password\":\"%s\"}";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        rolRepository.deleteAll();

        Rol rol = new Rol();
        rol.setNombre("Administrador");
        rol.setDescripcion("Rol de prueba para rate limiting");
        Rol savedRol = rolRepository.save(rol);

        User user = new User();
        user.setUsername("ratelimit@novafacts.com");
        user.setPassword(passwordEncoder.encode("CorrectPass123!"));
        user.setNombre("Rate Limit Tester");
        user.setRol(savedRol);
        user.setActivo(true);
        userRepository.save(user);
    }

    @Test
    void loginRateLimiting_fullSequence() throws Exception {
        // 1 & 4: a normal login, while capacity remains, succeeds and is not blocked.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_JSON.formatted("ratelimit@novafacts.com", "CorrectPass123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());

        // 2: invalid credentials, still below the limit, behave exactly as before (401, not 429).
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_JSON.formatted("ratelimit@novafacts.com", "wrong")))
                .andExpect(status().isUnauthorized());

        // Consumes the 3rd and final token of this test's capacity=3 budget.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_JSON.formatted("ratelimit@novafacts.com", "wrong")))
                .andExpect(status().isUnauthorized());

        // 3: bucket is now exhausted — the next attempt is rate limited (429), even with
        // fully correct credentials, proving the limiter acts on request volume alone and
        // is not bypassed by finally guessing the right password.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_JSON.formatted("ratelimit@novafacts.com", "CorrectPass123!")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").exists());

        // 5: a completely unrelated, unauthenticated endpoint is unaffected by the
        // exhausted login bucket — the filter only ever inspects POST /api/auth/login.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
