package com.novafacts.backend.temporada;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "admin@test.com", roles = {"ADMINISTRADOR"})
class TemporadaControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TemporadaRepository temporadaRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private DevolucionRepository devolucionRepository;
    @Autowired private NotaCreditoRepository notaCreditoRepository;
    @Autowired private AnticipoRepository anticipoRepository;
    @Autowired private PenalidadRepository penalidadRepository;
    @Autowired private FacturaRepository facturaRepository;
    @Autowired private CanalRepository canalRepository;
    @Autowired private PropertyRepository propertyRepository;
    @Autowired private PoliticaCancelacionRepository politicaRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private ObjectMapper objectMapper;

    private Temporada savedTemporada;

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

        Temporada temporada = new Temporada();
        temporada.setNombre("Temporada Alta 2028");
        temporada.setFechaInicio(LocalDate.of(2028, 1, 1));
        temporada.setFechaFin(LocalDate.of(2028, 12, 31));
        savedTemporada = temporadaRepository.save(temporada);
    }

    @Test
    void delete_temporada_without_reservations_returns_204() throws Exception {
        mockMvc.perform(delete("/api/temporadas/" + savedTemporada.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_temporada_with_existing_reservation_returns_409() throws Exception {
        Rol rol = new Rol();
        rol.setNombre("Administrador");
        Rol savedRol = rolRepository.save(rol);

        User user = new User();
        user.setUsername("admin@test.com");
        user.setPassword("$2a$10$irrelevant");
        user.setNombre("Admin Test");
        user.setRol(savedRol);
        User savedUser = userRepository.save(user);

        Canal canal = new Canal();
        canal.setNombre("Canal Test");
        canal.setTipo("Directo");
        Canal savedCanal = canalRepository.save(canal);

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

        mockMvc.perform(delete("/api/temporadas/" + savedTemporada.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(
                        "No se puede eliminar la temporada porque existen reservas que la referencian."));
    }

    @Test
    void delete_temporada_with_nonexistent_id_returns_404() throws Exception {
        mockMvc.perform(delete("/api/temporadas/99999"))
                .andExpect(status().isNotFound());
    }

    // ── M-7: overlapping season validation ──────────────────────────────────────
    // savedTemporada spans 2028-01-01..2028-12-31 (see setUp()).

    @Test
    void create_partially_overlapping_temporada_returns_409() throws Exception {
        mockMvc.perform(post("/api/temporadas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(temporadaBody("Solapada", "2028-06-01", "2029-06-01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(
                        "El rango de fechas se superpone con una temporada existente"));
    }

    @Test
    void create_identical_range_returns_409() throws Exception {
        mockMvc.perform(post("/api/temporadas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(temporadaBody("Idéntica", "2028-01-01", "2028-12-31")))
                .andExpect(status().isConflict());
    }

    @Test
    void create_contained_range_returns_409() throws Exception {
        mockMvc.perform(post("/api/temporadas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(temporadaBody("Contenida", "2028-03-01", "2028-04-01")))
                .andExpect(status().isConflict());
    }

    @Test
    void create_adjacent_range_returns_201() throws Exception {
        // Starts exactly where savedTemporada ends — touching boundaries are not overlap.
        mockMvc.perform(post("/api/temporadas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(temporadaBody("Adyacente", "2028-12-31", "2029-06-01")))
                .andExpect(status().isCreated());
    }

    @Test
    void update_keeping_same_range_returns_200() throws Exception {
        mockMvc.perform(put("/api/temporadas/" + savedTemporada.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(temporadaBody("Temporada Alta 2028 renombrada", "2028-01-01", "2028-12-31")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Temporada Alta 2028 renombrada"));
    }

    @Test
    void update_overlapping_another_temporada_returns_409() throws Exception {
        String createResponse = mockMvc.perform(post("/api/temporadas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(temporadaBody("Otra Temporada", "2029-01-01", "2029-06-01")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Integer otraId = objectMapper.readTree(createResponse).get("id").asInt();

        mockMvc.perform(put("/api/temporadas/" + otraId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(temporadaBody("Otra Temporada", "2028-06-01", "2028-07-01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(
                        "El rango de fechas se superpone con una temporada existente"));
    }

    // ── L-3: unique season name validation (case-insensitive) ────────────────────
    // savedTemporada is named "Temporada Alta 2028" (see setUp()); ranges below are
    // chosen to not overlap with it, isolating the nombre check from M-7's overlap check.

    @Test
    void create_temporada_with_duplicate_nombre_returns_409() throws Exception {
        mockMvc.perform(post("/api/temporadas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(temporadaBody("Temporada Alta 2028", "2029-01-01", "2029-06-01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Ya existe una temporada con ese nombre"));
    }

    @Test
    void create_temporada_with_duplicate_nombre_different_case_returns_409() throws Exception {
        mockMvc.perform(post("/api/temporadas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(temporadaBody("temporada alta 2028", "2029-01-01", "2029-06-01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Ya existe una temporada con ese nombre"));
    }

    @Test
    void update_temporada_keeping_same_nombre_returns_200() throws Exception {
        mockMvc.perform(put("/api/temporadas/" + savedTemporada.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(temporadaBody("Temporada Alta 2028", "2028-01-01", "2028-12-31")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Temporada Alta 2028"));
    }

    @Test
    void update_temporada_with_duplicate_nombre_returns_409() throws Exception {
        String createResponse = mockMvc.perform(post("/api/temporadas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(temporadaBody("Temporada Baja 2028", "2029-01-01", "2029-06-01")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Integer otraId = objectMapper.readTree(createResponse).get("id").asInt();

        mockMvc.perform(put("/api/temporadas/" + otraId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(temporadaBody("Temporada Alta 2028", "2029-01-01", "2029-06-01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Ya existe una temporada con ese nombre"));
    }

    // ── L-8: fecha_inicio < fecha_fin range validation ────────────────────────────

    @Test
    void create_temporada_with_fechaInicio_after_fechaFin_returns_400() throws Exception {
        mockMvc.perform(post("/api/temporadas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(temporadaBody("Rango Invertido", "2030-06-01", "2030-01-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "La fecha de inicio debe ser anterior a la fecha de fin"));
    }

    @Test
    void create_temporada_with_equal_dates_returns_400() throws Exception {
        mockMvc.perform(post("/api/temporadas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(temporadaBody("Rango de un día", "2030-06-01", "2030-06-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "La fecha de inicio debe ser anterior a la fecha de fin"));
    }

    @Test
    void save_temporada_with_invalid_fecha_rango_via_repository_throws_data_integrity_violation() {
        // Bypasses TemporadaService.validarFechas() entirely, simulating a write path
        // (e.g. test fixtures, seeder, manual SQL) that skips service-layer validation.
        // Only the V15 CHECK constraint stands between this and an invalid row.
        Temporada temporada = new Temporada();
        temporada.setNombre("Temporada Corrupta");
        temporada.setFechaInicio(LocalDate.of(2030, 6, 1));
        temporada.setFechaFin(LocalDate.of(2030, 1, 1));

        assertThrows(DataIntegrityViolationException.class, () -> temporadaRepository.save(temporada));
    }

    private String temporadaBody(String nombre, String fechaInicio, String fechaFin) {
        return """
                {
                    "nombre": "%s",
                    "fechaInicio": "%s",
                    "fechaFin": "%s"
                }
                """.formatted(nombre, fechaInicio, fechaFin);
    }
}
