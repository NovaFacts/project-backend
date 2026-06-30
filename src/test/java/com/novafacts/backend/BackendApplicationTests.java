package com.novafacts.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackendApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the full Spring context (datasource, Flyway, Security, JPA)
        // starts without errors against the real PostgreSQL schema via Testcontainers.
    }
}
