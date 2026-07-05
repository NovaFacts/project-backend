package com.novafacts.backend.canal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novafacts.backend.anticipo.repository.AnticipoRepository;
import com.novafacts.backend.auth.entity.User;
import com.novafacts.backend.auth.repository.UserRepository;
import com.novafacts.backend.canal.entity.Canal;
import com.novafacts.backend.canal.repository.CanalRepository;
import com.novafacts.backend.devolucion.repository.DevolucionRepository;
import com.novafacts.backend.factura.repository.FacturaRepository;
import com.novafacts.backend.notacredito.repository.NotaCreditoRepository;
import com.novafacts.backend.penalidad.repository.PenalidadRepository;
import com.novafacts.backend.politicacancelacion.entity.PoliticaCancelacion;
import com.novafacts.backend.politicacancelacion.repository.PoliticaCancelacionRepository;
import com.novafacts.backend.property.entity.Property;
import com.novafacts.backend.property.repository.PropertyRepository;
import com.novafacts.backend.reservation.entity.Reservation;
import com.novafacts.backend.reservation.entity.ReservationStatus;
import com.novafacts.backend.reservation.repository.ReservationRepository;
import com.novafacts.backend.rol.entity.Rol;
import com.novafacts.backend.rol.repository.RolRepository;
import com.novafacts.backend.temporada.entity.Temporada;
import com.novafacts.backend.temporada.repository.TemporadaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "admin@test.com", roles = {"ADMINISTRADOR"})
class CanalControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CanalRepository canalRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private DevolucionRepository devolucionRepository;
    @Autowired private NotaCreditoRepository notaCreditoRepository;
    @Autowired private AnticipoRepository anticipoRepository;
    @Autowired private PenalidadRepository penalidadRepository;
    @Autowired private FacturaRepository facturaRepository;
    @Autowired private PropertyRepository propertyRepository;
    @Autowired private TemporadaRepository temporadaRepository;
    @Autowired private PoliticaCancelacionRepository politicaRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private ObjectMapper objectMapper;

    private Canal savedCanal;

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

        Canal canal = new Canal();
        canal.setNombre("Canal Test");
        canal.setTipo("Directo");
        savedCanal = canalRepository.save(canal);
    }

    // ── GET /api/canales/{id} ──────────────────────────────────────────────────

    @Test
    void get_canal_by_id_returns_200() throws Exception {
        mockMvc.perform(get("/api/canales/" + savedCanal.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedCanal.getId()))
                .andExpect(jsonPath("$.nombre").value("Canal Test"))
                .andExpect(jsonPath("$.tipo").value("Directo"));
    }

    @Test
    void get_canal_by_id_with_nonexistent_id_returns_404() throws Exception {
        mockMvc.perform(get("/api/canales/99999"))
                .andExpect(status().isNotFound());
    }

    // ── POST /api/canales ──────────────────────────────────────────────────────

    @Test
    void create_canal_returns_201() throws Exception {
        mockMvc.perform(post("/api/canales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(canalBody("Instagram DM", "Directo")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Instagram DM"))
                .andExpect(jsonPath("$.tipo").value("Directo"));
    }

    @Test
    void create_canal_with_blank_nombre_returns_400() throws Exception {
        mockMvc.perform(post("/api/canales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(canalBody("", "Directo")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_canal_with_blank_tipo_returns_400() throws Exception {
        mockMvc.perform(post("/api/canales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(canalBody("Vrbo", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_canal_with_duplicate_nombre_returns_409() throws Exception {
        mockMvc.perform(post("/api/canales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(canalBody("Canal Test", "Plataforma")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Ya existe un canal con ese nombre"));
    }

    @Test
    void create_canal_with_duplicate_nombre_different_case_returns_409() throws Exception {
        mockMvc.perform(post("/api/canales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(canalBody("canal test", "Plataforma")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Ya existe un canal con ese nombre"));
    }

    // ── PUT /api/canales/{id} ──────────────────────────────────────────────────

    @Test
    void update_canal_returns_200() throws Exception {
        mockMvc.perform(put("/api/canales/" + savedCanal.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(canalBody("Canal Renombrado", "Plataforma")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Canal Renombrado"))
                .andExpect(jsonPath("$.tipo").value("Plataforma"));
    }

    @Test
    void update_canal_keeping_same_nombre_returns_200() throws Exception {
        mockMvc.perform(put("/api/canales/" + savedCanal.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(canalBody("Canal Test", "Plataforma")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("Plataforma"));
    }

    @Test
    void update_canal_with_duplicate_nombre_returns_409() throws Exception {
        String createResponse = mockMvc.perform(post("/api/canales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(canalBody("Otro Canal", "Directo")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Integer otroId = objectMapper.readTree(createResponse).get("id").asInt();

        mockMvc.perform(put("/api/canales/" + otroId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(canalBody("Canal Test", "Directo")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Ya existe un canal con ese nombre"));
    }

    @Test
    void update_canal_with_nonexistent_id_returns_404() throws Exception {
        mockMvc.perform(put("/api/canales/99999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(canalBody("Canal Fantasma", "Directo")))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /api/canales/{id} ───────────────────────────────────────────────

    @Test
    void delete_canal_without_reservations_returns_204() throws Exception {
        mockMvc.perform(delete("/api/canales/" + savedCanal.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_canal_with_nonexistent_id_returns_404() throws Exception {
        mockMvc.perform(delete("/api/canales/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_canal_with_existing_reservation_returns_409() throws Exception {
        Rol rol = new Rol();
        rol.setNombre("Administrador");
        Rol savedRol = rolRepository.save(rol);

        User user = new User();
        user.setUsername("admin@test.com");
        user.setPassword("$2a$10$irrelevant");
        user.setNombre("Admin Test");
        user.setRol(savedRol);
        User savedUser = userRepository.save(user);

        Temporada temporada = new Temporada();
        temporada.setNombre("Temporada Test");
        temporada.setFechaInicio(LocalDate.of(2028, 1, 1));
        temporada.setFechaFin(LocalDate.of(2028, 12, 31));
        Temporada savedTemporada = temporadaRepository.save(temporada);

        Property property = new Property();
        property.setName("Villa Test");
        property.setAddress("Calle 1 # 2-3");
        Property savedProperty = propertyRepository.save(property);

        PoliticaCancelacion politica = new PoliticaCancelacion();
        politica.setPropiedad(savedProperty);
        politica.setNombre("Política Test");
        politica.setPorcentajeReembolso(new BigDecimal("50.00"));
        politica.setDiasAviso(3);
        PoliticaCancelacion savedPolitica = politicaRepository.save(politica);

        Reservation reservation = new Reservation();
        reservation.setCanal(savedCanal);
        reservation.setTemporada(savedTemporada);
        reservation.setPoliticaCancelacion(savedPolitica);
        reservation.setUsuarioCreador(savedUser);
        reservation.setPropertyId(savedProperty.getId());
        reservation.setClienteNombre("Cliente Test");
        reservation.setMontoTotal(new BigDecimal("500000.00"));
        reservation.setCheckIn(LocalDate.of(2028, 3, 1));
        reservation.setCheckOut(LocalDate.of(2028, 3, 5));
        reservation.setGuestCount(2);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservationRepository.save(reservation);

        mockMvc.perform(delete("/api/canales/" + savedCanal.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(
                        "No se puede eliminar el canal porque existen reservas que lo referencian."));
    }

    private String canalBody(String nombre, String tipo) {
        return """
                {
                    "nombre": "%s",
                    "tipo": "%s"
                }
                """.formatted(nombre, tipo);
    }
}
