package com.novafacts.backend.reservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novafacts.backend.anticipo.entity.Anticipo;
import com.novafacts.backend.anticipo.entity.AnticipoEstado;
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
@WithMockUser(username = "user")
class ReservationControllerTest {

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

    private Property savedProperty;
    private Canal savedCanal;
    private Temporada savedTemporada;
    private PoliticaCancelacion savedPolitica;

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
        rol.setNombre("TestRol");
        Rol savedRol = rolRepository.save(rol);

        // Must match the @WithMockUser(username = "user") so ReservationService
        // can find this User from the SecurityContext
        User user = new User();
        user.setUsername("user");
        user.setPassword("$2a$10$irrelevant");
        user.setNombre("Test User");
        user.setRol(savedRol);
        userRepository.save(user);

        Canal canal = new Canal();
        canal.setNombre("Canal Test");
        canal.setTipo("Directo");
        savedCanal = canalRepository.save(canal);

        Temporada temporada = new Temporada();
        temporada.setNombre("Temporada Test");
        temporada.setFechaInicio(LocalDate.of(2027, 1, 1));
        temporada.setFechaFin(LocalDate.of(2027, 12, 31));
        savedTemporada = temporadaRepository.save(temporada);

        Property property = new Property();
        property.setName("Villa Reserva Test");
        property.setAddress("Avenida 10 # 50-60");
        savedProperty = propertyRepository.save(property);

        PoliticaCancelacion politica = new PoliticaCancelacion();
        politica.setPropiedad(savedProperty);
        politica.setNombre("Política Test");
        politica.setPorcentajeReembolso(new BigDecimal("50.00"));
        politica.setDiasAviso(3);
        savedPolitica = politicaRepository.save(politica);
    }

    private String createBody(String checkIn, String checkOut) {
        return """
                {
                    "canalId": %d,
                    "temporadaId": %d,
                    "politicaCancelacionId": %d,
                    "propertyId": %d,
                    "clienteNombre": "Juan García",
                    "clienteEmail": "juan@example.com",
                    "montoTotal": 500000,
                    "checkIn": "%s",
                    "checkOut": "%s",
                    "guestCount": 2
                }
                """.formatted(savedCanal.getId(), savedTemporada.getId(),
                              savedPolitica.getId(), savedProperty.getId(),
                              checkIn, checkOut);
    }

    private String updateBody(String checkIn, String checkOut, int guestCount, String status) {
        return """
                {
                    "canalId": %d,
                    "temporadaId": %d,
                    "politicaCancelacionId": %d,
                    "propertyId": %d,
                    "clienteNombre": "Juan García",
                    "montoTotal": 600000,
                    "checkIn": "%s",
                    "checkOut": "%s",
                    "guestCount": %d,
                    "status": "%s"
                }
                """.formatted(savedCanal.getId(), savedTemporada.getId(),
                              savedPolitica.getId(), savedProperty.getId(),
                              checkIn, checkOut, guestCount, status);
    }

    @Test
    void create_reservation_returns_201() throws Exception {
        mockMvc.perform(post("/api/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("2027-06-01", "2027-06-06")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.clienteNombre").value("Juan García"))
                .andExpect(jsonPath("$.canalId").value(savedCanal.getId()))
                .andExpect(jsonPath("$.propertyId").value(savedProperty.getId()));
    }

    @Test
    void create_reservation_with_zero_montoTotal_returns_400() throws Exception {
        String body = createBody("2027-06-01", "2027-06-06").replace("\"montoTotal\": 500000", "\"montoTotal\": 0");

        mockMvc.perform(post("/api/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_reservation_with_minimum_montoTotal_returns_201() throws Exception {
        String body = createBody("2027-06-01", "2027-06-06").replace("\"montoTotal\": 500000", "\"montoTotal\": 0.01");

        mockMvc.perform(post("/api/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void overlapping_reservation_returns_409() throws Exception {
        mockMvc.perform(post("/api/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("2027-07-01", "2027-07-08")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("2027-07-04", "2027-07-11")))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelled_reservation_does_not_block_dates() throws Exception {
        String createResponse = mockMvc.perform(post("/api/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("2027-08-10", "2027-08-15")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long reservationId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(put("/api/reservas/" + reservationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("2027-08-10", "2027-08-15", 2, "CANCELLED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(post("/api/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("2027-08-10", "2027-08-15")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void update_reservation_returns_200() throws Exception {
        String createResponse = mockMvc.perform(post("/api/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("2027-09-01", "2027-09-05")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long reservationId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(put("/api/reservas/" + reservationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("2027-09-10", "2027-09-15", 4, "CONFIRMED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestCount").value(4))
                .andExpect(jsonPath("$.checkIn").value("2027-09-10"))
                .andExpect(jsonPath("$.checkOut").value("2027-09-15"));
    }

    @Test
    void update_reservation_with_zero_montoTotal_returns_400() throws Exception {
        String createResponse = mockMvc.perform(post("/api/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("2027-09-20", "2027-09-25")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long reservationId = objectMapper.readTree(createResponse).get("id").asLong();

        String body = updateBody("2027-09-20", "2027-09-25", 2, "CONFIRMED")
                .replace("\"montoTotal\": 600000", "\"montoTotal\": 0");

        mockMvc.perform(put("/api/reservas/" + reservationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_reservation_property_with_anticipo_returns_409() throws Exception {
        // Create a reservation on savedProperty
        String createResponse = mockMvc.perform(post("/api/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("2027-11-01", "2027-11-05")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long reservationId = objectMapper.readTree(createResponse).get("id").asLong();

        // Directly persist an anticipo to create financial history for this reservation
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        User user = userRepository.findByUsername("user").orElseThrow();
        Anticipo anticipo = new Anticipo();
        anticipo.setReserva(reservation);
        anticipo.setUsuario(user);
        anticipo.setMonto(new BigDecimal("200000.00"));
        anticipo.setFechaPago(LocalDate.of(2027, 10, 15));
        anticipo.setEstado(AnticipoEstado.REGISTRADO);
        anticipoRepository.save(anticipo);

        // Set up a second property with its own cancellation policy (required for a valid update body)
        Property otherProperty = new Property();
        otherProperty.setName("Otra Propiedad");
        otherProperty.setAddress("Calle 99 # 1-2");
        Property savedOtherProperty = propertyRepository.save(otherProperty);

        PoliticaCancelacion otherPolitica = new PoliticaCancelacion();
        otherPolitica.setPropiedad(savedOtherProperty);
        otherPolitica.setNombre("Política Otra");
        otherPolitica.setPorcentajeReembolso(new BigDecimal("30.00"));
        otherPolitica.setDiasAviso(5);
        PoliticaCancelacion savedOtherPolitica = politicaRepository.save(otherPolitica);

        // Attempt to change propertyId — must be rejected because an anticipo already exists
        String bodyWithDifferentProperty = """
                {
                    "canalId": %d,
                    "temporadaId": %d,
                    "politicaCancelacionId": %d,
                    "propertyId": %d,
                    "clienteNombre": "Juan García",
                    "montoTotal": 600000,
                    "checkIn": "2027-11-01",
                    "checkOut": "2027-11-05",
                    "guestCount": 2,
                    "status": "CONFIRMED"
                }
                """.formatted(savedCanal.getId(), savedTemporada.getId(),
                              savedOtherPolitica.getId(), savedOtherProperty.getId());

        mockMvc.perform(put("/api/reservas/" + reservationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithDifferentProperty))
                .andExpect(status().isConflict());
    }

    @Test
    void update_reservation_property_without_financial_history_returns_200() throws Exception {
        // Create a reservation on savedProperty with no financial records attached
        String createResponse = mockMvc.perform(post("/api/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("2027-12-01", "2027-12-05")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long reservationId = objectMapper.readTree(createResponse).get("id").asLong();

        // Set up a second property with its own cancellation policy
        Property otherProperty = new Property();
        otherProperty.setName("Segunda Propiedad");
        otherProperty.setAddress("Carrera 5 # 10-20");
        Property savedOtherProperty = propertyRepository.save(otherProperty);

        PoliticaCancelacion otherPolitica = new PoliticaCancelacion();
        otherPolitica.setPropiedad(savedOtherProperty);
        otherPolitica.setNombre("Política Segunda");
        otherPolitica.setPorcentajeReembolso(new BigDecimal("20.00"));
        otherPolitica.setDiasAviso(2);
        PoliticaCancelacion savedOtherPolitica = politicaRepository.save(otherPolitica);

        // Changing the property is allowed because there is no financial history
        String bodyWithDifferentProperty = """
                {
                    "canalId": %d,
                    "temporadaId": %d,
                    "politicaCancelacionId": %d,
                    "propertyId": %d,
                    "clienteNombre": "Juan García",
                    "montoTotal": 600000,
                    "checkIn": "2027-12-01",
                    "checkOut": "2027-12-05",
                    "guestCount": 2,
                    "status": "CONFIRMED"
                }
                """.formatted(savedCanal.getId(), savedTemporada.getId(),
                              savedOtherPolitica.getId(), savedOtherProperty.getId());

        mockMvc.perform(put("/api/reservas/" + reservationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithDifferentProperty))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propertyId").value(savedOtherProperty.getId()));
    }

    @Test
    void delete_reservation_returns_204_and_subsequent_get_returns_404() throws Exception {
        String createResponse = mockMvc.perform(post("/api/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("2027-10-01", "2027-10-05")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long reservationId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(delete("/api/reservas/" + reservationId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/reservas/" + reservationId))
                .andExpect(status().isNotFound());
    }

    @Test
    void legacy_reservations_path_returns_200() throws Exception {
        mockMvc.perform(get("/api/reservations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
