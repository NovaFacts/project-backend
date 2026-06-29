package com.novafacts.backend.anticipo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novafacts.backend.anticipo.entity.Anticipo;
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
@WithMockUser(username = "contador@test.com", roles = {"CONTADOR"})
class AnticipoControllerTest {

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

        // Username must match @WithMockUser(username) so the service can resolve the user
        User user = new User();
        user.setUsername("contador@test.com");
        user.setPassword("$2a$10$irrelevant");
        user.setNombre("Contador Test");
        user.setRol(savedRol);
        User savedUser = userRepository.save(user);

        Canal canal = new Canal();
        canal.setNombre("Canal Anticipo");
        canal.setTipo("Directo");
        Canal savedCanal = canalRepository.save(canal);

        Temporada temporada = new Temporada();
        temporada.setNombre("Temporada Anticipo");
        temporada.setFechaInicio(LocalDate.of(2027, 1, 1));
        temporada.setFechaFin(LocalDate.of(2027, 12, 31));
        Temporada savedTemporada = temporadaRepository.save(temporada);

        Property property = new Property();
        property.setName("Finca Anticipo Test");
        property.setAddress("Vereda Los Pinos");
        Property savedProperty = propertyRepository.save(property);

        PoliticaCancelacion politica = new PoliticaCancelacion();
        politica.setPropiedad(savedProperty);
        politica.setNombre("Política Anticipo");
        politica.setPorcentajeReembolso(new BigDecimal("50.00"));
        politica.setDiasAviso(3);
        PoliticaCancelacion savedPolitica = politicaRepository.save(politica);

        Reservation reservation = new Reservation();
        reservation.setCanal(savedCanal);
        reservation.setTemporada(savedTemporada);
        reservation.setPoliticaCancelacion(savedPolitica);
        reservation.setUsuarioCreador(savedUser);
        reservation.setPropertyId(savedProperty.getId());
        reservation.setClienteNombre("Carlos Muñoz");
        reservation.setMontoTotal(new BigDecimal("800000.00"));
        reservation.setCheckIn(LocalDate.of(2027, 5, 1));
        reservation.setCheckOut(LocalDate.of(2027, 5, 5));
        reservation.setGuestCount(2);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        savedReservation = reservationRepository.save(reservation);
    }

    private String anticipoBody() {
        return """
                {
                    "reservaId": %d,
                    "monto": 300000.00,
                    "fechaPago": "2027-04-15",
                    "metodoPago": "transferencia"
                }
                """.formatted(savedReservation.getId());
    }

    @Test
    void create_anticipo_returns_201_with_estado_registrado() throws Exception {
        mockMvc.perform(post("/api/anticipos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(anticipoBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservaId").value(savedReservation.getId()))
                .andExpect(jsonPath("$.monto").value(300000.0))
                .andExpect(jsonPath("$.metodoPago").value("transferencia"))
                .andExpect(jsonPath("$.estado").value("registrado"))
                .andExpect(jsonPath("$.usuarioNombre").value("Contador Test"))
                .andExpect(jsonPath("$.registradoEn").exists());
    }

    @Test
    void create_anticipo_with_missing_fields_returns_400() throws Exception {
        mockMvc.perform(post("/api/anticipos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reservaId": %d}
                                """.formatted(savedReservation.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_anticipo_with_nonexistent_reserva_returns_404() throws Exception {
        mockMvc.perform(post("/api/anticipos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "reservaId": 99999,
                                    "monto": 100000.00,
                                    "fechaPago": "2027-04-15"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_anticipo_by_id_returns_200() throws Exception {
        String createResponse = mockMvc.perform(post("/api/anticipos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(anticipoBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long anticipoId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(get("/api/anticipos/" + anticipoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(anticipoId))
                .andExpect(jsonPath("$.estado").value("registrado"));
    }

    @Test
    void get_anticipos_by_reserva_returns_list() throws Exception {
        mockMvc.perform(post("/api/anticipos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(anticipoBody()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/anticipos/by-reserva/" + savedReservation.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reservaId").value(savedReservation.getId()));
    }

    @Test
    void delete_anticipo_returns_204() throws Exception {
        String createResponse = mockMvc.perform(post("/api/anticipos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(anticipoBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long anticipoId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(delete("/api/anticipos/" + anticipoId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/anticipos/" + anticipoId))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_applied_anticipo_returns_409() throws Exception {
        // Save an anticipo directly with estado "aplicado" to simulate post-invoice state
        Anticipo applied = new Anticipo();
        applied.setReserva(savedReservation);
        applied.setUsuario(userRepository.findByUsername("contador@test.com").orElseThrow());
        applied.setMonto(new BigDecimal("200000.00"));
        applied.setFechaPago(LocalDate.of(2027, 4, 10));
        applied.setEstado("aplicado");
        Anticipo saved = anticipoRepository.save(applied);

        mockMvc.perform(delete("/api/anticipos/" + saved.getId()))
                .andExpect(status().isConflict());
    }
}
