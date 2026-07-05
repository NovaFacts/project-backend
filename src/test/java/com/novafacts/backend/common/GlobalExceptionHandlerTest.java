package com.novafacts.backend.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * H-404: an unknown route used to return 500 because NoResourceFoundException (thrown
 * by Spring's auto-registered catch-all static-resource handler once DispatcherServlet
 * finds no matching @RequestMapping) fell through to GlobalExceptionHandler's generic
 * Exception.class handler, which discarded the exception's own self-reported 404 status.
 * These tests cover the fix and pin down the surrounding behavior that must stay
 * unchanged: authentication is still checked before MVC dispatch even happens, and a
 * real business "not found" (ResponseStatusException, from an existing controller
 * mapping) is unaffected since it's a different exception type entirely.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void unknownRoute_authenticated_returns404NotFound() throws Exception {
        mockMvc.perform(get("/api/totally-fake-endpoint-12345"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Recurso no encontrado"));
    }

    @Test
    void unknownRoute_unauthenticated_returns401NotDispatchedToMvc() throws Exception {
        // No @RequestMapping matches this path and it isn't permitAll, so
        // AuthorizationFilter rejects it before DispatcherServlet ever runs —
        // this must stay 401, not become 404, since the request is never
        // authenticated at all.
        mockMvc.perform(get("/api/totally-fake-endpoint-12345"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void unexposedActuatorEndpoint_authenticated_returns404NotFound() throws Exception {
        // /actuator/env is never web-exposed (management.endpoints.web.exposure.include
        // only lists "health"), so it hits the identical NoResourceFoundException path.
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Recurso no encontrado"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void realBusinessNotFound_stillReturnsItsOwnSpecificMessage() throws Exception {
        // A real controller mapping exists here; the 404 comes from
        // ResponseStatusException in the service layer, a completely different
        // exception type from NoResourceFoundException, and must keep its own
        // specific message rather than the new generic one.
        mockMvc.perform(get("/api/reservas/999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Reserva no encontrada"));
    }
}
