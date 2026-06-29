package com.novafacts.backend.reservation;

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
        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("2027-06-01", "2027-06-06")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.clienteNombre").value("Juan García"))
                .andExpect(jsonPath("$.canalId").value(savedCanal.getId()))
                .andExpect(jsonPath("$.propertyId").value(savedProperty.getId()));
    }

    @Test
    void overlapping_reservation_returns_409() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("2027-07-01", "2027-07-08")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("2027-07-04", "2027-07-11")))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelled_reservation_does_not_block_dates() throws Exception {
        String createResponse = mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("2027-08-10", "2027-08-15")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long reservationId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(put("/api/reservations/" + reservationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("2027-08-10", "2027-08-15", 2, "CANCELLED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("2027-08-10", "2027-08-15")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void update_reservation_returns_200() throws Exception {
        String createResponse = mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("2027-09-01", "2027-09-05")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long reservationId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(put("/api/reservations/" + reservationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("2027-09-10", "2027-09-15", 4, "CONFIRMED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestCount").value(4))
                .andExpect(jsonPath("$.checkIn").value("2027-09-10"))
                .andExpect(jsonPath("$.checkOut").value("2027-09-15"));
    }

    @Test
    void delete_reservation_returns_204_and_subsequent_get_returns_404() throws Exception {
        String createResponse = mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("2027-10-01", "2027-10-05")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long reservationId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(delete("/api/reservations/" + reservationId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/reservations/" + reservationId))
                .andExpect(status().isNotFound());
    }
}
