package com.novafacts.backend.rol;

import com.novafacts.backend.anticipo.repository.AnticipoRepository;
import com.novafacts.backend.auth.repository.UserRepository;
import com.novafacts.backend.canal.repository.CanalRepository;
import com.novafacts.backend.devolucion.repository.DevolucionRepository;
import com.novafacts.backend.factura.repository.FacturaRepository;
import com.novafacts.backend.notacredito.repository.NotaCreditoRepository;
import com.novafacts.backend.penalidad.repository.PenalidadRepository;
import com.novafacts.backend.politicacancelacion.repository.PoliticaCancelacionRepository;
import com.novafacts.backend.property.repository.PropertyRepository;
import com.novafacts.backend.reservation.repository.ReservationRepository;
import com.novafacts.backend.rol.entity.Rol;
import com.novafacts.backend.rol.repository.RolRepository;
import com.novafacts.backend.temporada.repository.TemporadaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * H-2 (AUDIT_v5): the rol module previously had zero automated test coverage.
 * RolController exposes only GET /api/roles (no create/update/delete mapping exists
 * at all, confirmed by direct code inspection — the module is inherently read-only
 * at the API surface), restricted to ADMINISTRADOR by SecurityConfig. Unlike the
 * financial modules, RolService.listar() never resolves the authenticated user from
 * SecurityContextHolder, so — unlike AnticipoControllerTest/FacturaControllerTest/etc.
 * — no real persisted User row is needed here for authentication itself:
 * @WithMockUser fabricates the security context directly for MockMvc, independent
 * of the database.
 *
 * RolRepository.findAll() has no ORDER BY, so response ordering is not guaranteed by
 * the application; assertions below use Hamcrest's containsInAnyOrder rather than
 * asserting a specific sequence. Role ids are never hardcoded anywhere in the
 * codebase (AdminUserInitializer/DevelopmentDataSeeder both look roles up by nombre),
 * so this test verifies ids are correctly round-tripped from what was actually
 * persisted, not a literal the application itself doesn't guarantee.
 *
 * The full repository cleanup chain below (identical order to every other controller
 * test in this codebase) is required even though this module has no direct
 * relationship to reservations/financial records: this Spring context's own
 * AdminUserInitializer persists a usuario row on every startup, and — since
 * @SpringBootTest reuses one shared context/database across the whole test run —
 * whichever test class ran previously may also have left reservation/financial rows
 * referencing a usuario. Deleting roles or users before all of that is cleared
 * violates fk_usuario_rol / fk_reserva_usuario and similar constraints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RolControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DevolucionRepository devolucionRepository;
    @Autowired private NotaCreditoRepository notaCreditoRepository;
    @Autowired private AnticipoRepository anticipoRepository;
    @Autowired private PenalidadRepository penalidadRepository;
    @Autowired private FacturaRepository facturaRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private PoliticaCancelacionRepository politicaRepository;
    @Autowired private PropertyRepository propertyRepository;
    @Autowired private TemporadaRepository temporadaRepository;
    @Autowired private CanalRepository canalRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RolRepository rolRepository;

    private Rol administrador;
    private Rol contador;
    private Rol auxiliarContable;
    private Rol recepcionista;

    @BeforeEach
    void setUp() {
        devolucionRepository.deleteAll();
        notaCreditoRepository.deleteAll();
        anticipoRepository.deleteAll();
        penalidadRepository.deleteAll();
        facturaRepository.deleteAll();
        reservationRepository.deleteAll();
        politicaRepository.deleteAll();
        propertyRepository.deleteAll();
        temporadaRepository.deleteAll();
        canalRepository.deleteAll();
        userRepository.deleteAll();
        rolRepository.deleteAll();

        administrador    = saveRol("Administrador", "Control total del sistema: usuarios, propiedades, configuración");
        contador         = saveRol("Contador", "Gestión financiera: anticipos, facturas, devoluciones");
        auxiliarContable = saveRol("Auxiliar contable", "Apoyo contable: registro de reservas y anticipos");
        recepcionista    = saveRol("Recepcionista", "Atención al cliente: gestión de reservas y consultas");
    }

    private Rol saveRol(String nombre, String descripcion) {
        Rol rol = new Rol();
        rol.setNombre(nombre);
        rol.setDescripcion(descripcion);
        return rolRepository.save(rol);
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void getRoles_asAdministrador_returns200WithAllSeededRoles() throws Exception {
        mockMvc.perform(get("/api/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[*].nombre", containsInAnyOrder(
                        "Administrador", "Contador", "Auxiliar contable", "Recepcionista")));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void getRoles_responseStructure_matchesDto() throws Exception {
        mockMvc.perform(get("/api/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].nombre").exists())
                .andExpect(jsonPath("$[0].descripcion").exists())
                .andExpect(jsonPath("$[*].descripcion", containsInAnyOrder(
                        "Control total del sistema: usuarios, propiedades, configuración",
                        "Gestión financiera: anticipos, facturas, devoluciones",
                        "Apoyo contable: registro de reservas y anticipos",
                        "Atención al cliente: gestión de reservas y consultas")));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void getRoles_idsMatchPersistedEntities() throws Exception {
        mockMvc.perform(get("/api/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(
                        administrador.getId(), contador.getId(),
                        auxiliarContable.getId(), recepcionista.getId())));
    }

    @Test
    void getRoles_unauthenticated_returns401() throws Exception {
        // No @WithMockUser at all — the request carries no authentication whatsoever,
        // matching SecurityConfig's authenticationEntryPoint (HttpStatusEntryPoint 401),
        // distinct from the 403 an authenticated-but-wrong-role request gets below.
        mockMvc.perform(get("/api/roles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CONTADOR")
    void getRoles_authenticatedWithoutAdminRole_returns403() throws Exception {
        // GET /api/roles/** is restricted to hasRole("ADMINISTRADOR"); an authenticated
        // Contador is correctly rejected by the AccessDeniedHandler (403), not 401.
        mockMvc.perform(get("/api/roles"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void getRoles_isReadOnly_repeatedCallsDoNotChangeState() throws Exception {
        // RolController exposes no create/update/delete mapping at all — this asserts
        // the observable consequence: repeated reads never change the persisted count.
        mockMvc.perform(get("/api/roles")).andExpect(status().isOk());
        mockMvc.perform(get("/api/roles")).andExpect(status().isOk());

        assertEquals(4, rolRepository.count());
    }
}
