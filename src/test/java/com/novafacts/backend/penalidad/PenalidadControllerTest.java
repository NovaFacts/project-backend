package com.novafacts.backend.penalidad;

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
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * H-1 (AUDIT_v5): the penalidad module previously had zero automated test coverage,
 * which is exactly why C-1 (a missing duplicate-penalty guard that allowed a real
 * financial overcharge) shipped undetected — none of the other financial modules
 * (Anticipo, Factura, Devolucion, NotaCredito) have this gap. This class mirrors
 * their existing structure/conventions exactly.
 *
 * duplicate_penalidad_returns_409_and_does_not_overcharge is the direct C-1
 * regression test: reverting the existsByReservaId guard in
 * PenalidadService.create() makes the second POST return 201 instead of 409 and
 * this test fails immediately.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "contador@test.com", roles = {"CONTADOR"})
class PenalidadControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DevolucionRepository devolucionRepository;
    @Autowired private NotaCreditoRepository notaCreditoRepository;
    @Autowired private AnticipoRepository anticipoRepository;
    @Autowired private PenalidadRepository penalidadRepository;
    @Autowired private FacturaRepository facturaRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private PropertyRepository propertyRepository;
    @Autowired private CanalRepository canalRepository;
    @Autowired private TemporadaRepository temporadaRepository;
    @Autowired private PoliticaCancelacionRepository politicaRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private ObjectMapper objectMapper;

    private Reservation savedReservation;
    private Canal savedCanal;
    private Temporada savedTemporada;
    private PoliticaCancelacion savedPolitica;
    private User savedUser;

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

        Rol rol = new Rol();
        rol.setNombre("Contador");
        Rol savedRol = rolRepository.save(rol);

        savedUser = new User();
        savedUser.setUsername("contador@test.com");
        savedUser.setPassword("$2a$10$irrelevant");
        savedUser.setNombre("Contador Test");
        savedUser.setRol(savedRol);
        savedUser = userRepository.save(savedUser);

        savedCanal = new Canal();
        savedCanal.setNombre("Canal Penalidad");
        savedCanal.setTipo("Directo");
        savedCanal = canalRepository.save(savedCanal);

        savedTemporada = new Temporada();
        savedTemporada.setNombre("Temporada Penalidad");
        savedTemporada.setFechaInicio(LocalDate.of(2027, 1, 1));
        savedTemporada.setFechaFin(LocalDate.of(2027, 12, 31));
        savedTemporada = temporadaRepository.save(savedTemporada);

        Property property = new Property();
        property.setName("Finca Penalidad Test");
        property.setAddress("Vereda El Rosal");
        Property savedProperty = propertyRepository.save(property);

        // 50% refund policy: on a 800,000 reservation, the true maximum penalty is
        // 800000 * (1 - 0.50) = 400,000.00 — used by the policy-cap test below.
        savedPolitica = new PoliticaCancelacion();
        savedPolitica.setPropiedad(savedProperty);
        savedPolitica.setNombre("Política Penalidad");
        savedPolitica.setPorcentajeReembolso(new BigDecimal("50.00"));
        savedPolitica.setDiasAviso(3);
        savedPolitica = politicaRepository.save(savedPolitica);

        // Penalidad.create() requires a CANCELLED reservation, unlike Anticipo/Factura's
        // CONFIRMED baseline — this is the module's own specific precondition.
        savedReservation = saveReservation(ReservationStatus.CANCELLED, new BigDecimal("800000.00"));
    }

    private Reservation saveReservation(ReservationStatus status, BigDecimal montoTotal) {
        Reservation reservation = new Reservation();
        reservation.setCanal(savedCanal);
        reservation.setTemporada(savedTemporada);
        reservation.setPoliticaCancelacion(savedPolitica);
        reservation.setUsuarioCreador(savedUser);
        reservation.setPropertyId(savedPolitica.getPropiedad().getId());
        reservation.setClienteNombre("Diana Restrepo");
        reservation.setMontoTotal(montoTotal);
        reservation.setCheckIn(LocalDate.of(2027, 6, 1));
        reservation.setCheckOut(LocalDate.of(2027, 6, 5));
        reservation.setGuestCount(2);
        reservation.setStatus(status);
        return reservationRepository.save(reservation);
    }

    private String penalidadBody(Long reservaId, String montoAprobado) {
        return """
                {
                    "reservaId": %d,
                    "montoSegunPolitica": %s,
                    "montoAprobado": %s,
                    "fechaCancelacion": "2027-05-20",
                    "motivo": "Cancelación fuera del plazo de aviso"
                }
                """.formatted(reservaId, montoAprobado, montoAprobado);
    }

    private String penalidadBody() {
        return penalidadBody(savedReservation.getId(), "300000.00");
    }

    @Test
    void create_penalidad_returns_201_with_calculated_fields() throws Exception {
        mockMvc.perform(post("/api/penalidades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(penalidadBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservaId").value(savedReservation.getId()))
                .andExpect(jsonPath("$.montoSegunPolitica").value(300000.0))
                .andExpect(jsonPath("$.montoAprobado").value(300000.0))
                .andExpect(jsonPath("$.montoCondonado").value(0))
                .andExpect(jsonPath("$.usuarioNombre").value("Contador Test"))
                .andExpect(jsonPath("$.motivo").value("Cancelación fuera del plazo de aviso"))
                .andExpect(jsonPath("$.calculadoEn").exists());
    }

    @Test
    void create_penalidad_on_non_cancelled_reservation_returns_409() throws Exception {
        Reservation confirmedReservation = saveReservation(ReservationStatus.CONFIRMED, new BigDecimal("800000.00"));

        mockMvc.perform(post("/api/penalidades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(penalidadBody(confirmedReservation.getId(), "100000.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Solo se puede crear una penalidad sobre una reserva cancelada"));
    }

    @Test
    void create_penalidad_exceeding_policy_maximum_returns_400() throws Exception {
        // True maximum is 400,000.00 (50% refund on 800,000); 500,000.00 exceeds it.
        // The message's monetary figures are formatted with String.format("%.2f", ...)
        // using the JVM default locale (no explicit Locale.US), so the decimal
        // separator itself is not asserted here — only the stable, locale-independent
        // parts of the message (this is a pre-existing formatting quirk, out of scope
        // for this test-coverage task; see the H-1 report for details).
        mockMvc.perform(post("/api/penalidades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(penalidadBody(savedReservation.getId(), "500000.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("supera el máximo permitido")))
                .andExpect(jsonPath("$.error", containsString("50% de reembolso")));
    }

    @Test
    void duplicate_penalidad_returns_409_and_does_not_overcharge() throws Exception {
        // C-1 (AUDIT_v5) regression test. Without the existsByReservaId guard in
        // PenalidadService.create(), this second request would return 201 and both
        // penalties would persist — reproducing the audit's confirmed overcharge.
        mockMvc.perform(post("/api/penalidades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(penalidadBody()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/penalidades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(penalidadBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Ya existe una penalidad para esta reserva"));

        // Belt-and-suspenders: assert directly against the database that only one
        // penalty row exists for this reservation, not just that the HTTP call failed.
        assertEquals(1, penalidadRepository.findByReservaId(savedReservation.getId()).size());
    }

    @Test
    void create_penalidad_for_nonexistent_reservation_returns_404() throws Exception {
        mockMvc.perform(post("/api/penalidades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(penalidadBody(99999L, "100000.00")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Reserva no encontrada"));
    }

    @Test
    void create_penalidad_with_missing_fields_returns_400() throws Exception {
        mockMvc.perform(post("/api/penalidades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservaId\": %d}".formatted(savedReservation.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_penalidad_by_id_returns_200() throws Exception {
        String createResponse = mockMvc.perform(post("/api/penalidades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(penalidadBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long penalidadId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(get("/api/penalidades/" + penalidadId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(penalidadId))
                .andExpect(jsonPath("$.reservaId").value(savedReservation.getId()));
    }

    @Test
    void get_penalidad_by_nonexistent_id_returns_404() throws Exception {
        mockMvc.perform(get("/api/penalidades/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Penalidad no encontrada"));
    }

    @Test
    void get_penalidades_by_reserva_returns_list() throws Exception {
        mockMvc.perform(post("/api/penalidades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(penalidadBody()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/penalidades/by-reserva/" + savedReservation.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reservaId").value(savedReservation.getId()));
    }

    @Test
    void get_all_penalidades_returns_list() throws Exception {
        mockMvc.perform(post("/api/penalidades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(penalidadBody()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/penalidades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].reservaId").value(savedReservation.getId()));
    }

    @Test
    void delete_penalidad_returns_204() throws Exception {
        String createResponse = mockMvc.perform(post("/api/penalidades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(penalidadBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long penalidadId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(delete("/api/penalidades/" + penalidadId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/penalidades/" + penalidadId))
                .andExpect(status().isNotFound());
    }
}
