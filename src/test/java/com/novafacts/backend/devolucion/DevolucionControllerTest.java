package com.novafacts.backend.devolucion;

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
class DevolucionControllerTest {

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
    @Autowired private ObjectMapper objectMapper;

    private Reservation savedReservation;
    private Anticipo savedAnticipo;

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

        User user = new User();
        user.setUsername("contador@test.com");
        user.setPassword("$2a$10$irrelevant");
        user.setNombre("Contador Test");
        user.setRol(savedRol);
        User savedUser = userRepository.save(user);

        Canal canal = new Canal();
        canal.setNombre("Canal Dev");
        canal.setTipo("Directo");
        Canal savedCanal = canalRepository.save(canal);

        Temporada temporada = new Temporada();
        temporada.setNombre("Temporada Dev");
        temporada.setFechaInicio(LocalDate.of(2027, 1, 1));
        temporada.setFechaFin(LocalDate.of(2027, 12, 31));
        Temporada savedTemporada = temporadaRepository.save(temporada);

        Property property = new Property();
        property.setName("Casa Dev Test");
        property.setAddress("Carrera Dev 10");
        Property savedProperty = propertyRepository.save(property);

        PoliticaCancelacion politica = new PoliticaCancelacion();
        politica.setPropiedad(savedProperty);
        politica.setNombre("Política Dev");
        politica.setPorcentajeReembolso(new BigDecimal("100.00"));
        politica.setDiasAviso(0);
        PoliticaCancelacion savedPolitica = politicaRepository.save(politica);

        Reservation reservation = new Reservation();
        reservation.setCanal(savedCanal);
        reservation.setTemporada(savedTemporada);
        reservation.setPoliticaCancelacion(savedPolitica);
        reservation.setUsuarioCreador(savedUser);
        reservation.setPropertyId(savedProperty.getId());
        reservation.setClienteNombre("Luis Martínez");
        reservation.setMontoTotal(new BigDecimal("400000.00"));
        reservation.setCheckIn(LocalDate.of(2027, 4, 1));
        reservation.setCheckOut(LocalDate.of(2027, 4, 5));
        reservation.setGuestCount(2);
        reservation.setStatus(ReservationStatus.CANCELLED);
        savedReservation = reservationRepository.save(reservation);

        // Create an anticipo directly for use in devolucion tests
        Anticipo anticipo = new Anticipo();
        anticipo.setReserva(savedReservation);
        anticipo.setUsuario(savedUser);
        anticipo.setMonto(new BigDecimal("200000.00"));
        anticipo.setFechaPago(LocalDate.of(2027, 3, 20));
        anticipo.setEstado("registrado");
        savedAnticipo = anticipoRepository.save(anticipo);
    }

    private String devolucionBody() {
        return """
                {
                    "reservaId": %d,
                    "anticipoId": %d,
                    "monto": 200000,
                    "metodo": "transferencia"
                }
                """.formatted(savedReservation.getId(), savedAnticipo.getId());
    }

    @Test
    void create_devolucion_returns_201_with_estado_pendiente() throws Exception {
        mockMvc.perform(post("/api/devoluciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(devolucionBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("pendiente"))
                .andExpect(jsonPath("$.monto").value(200000.0))
                .andExpect(jsonPath("$.metodo").value("transferencia"))
                .andExpect(jsonPath("$.reservaId").value(savedReservation.getId()))
                .andExpect(jsonPath("$.anticipoId").value(savedAnticipo.getId()))
                .andExpect(jsonPath("$.usuarioNombre").value("Contador Test"));
    }

    @Test
    void create_devolucion_with_missing_fields_returns_400() throws Exception {
        mockMvc.perform(post("/api/devoluciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservaId\": %d}".formatted(savedReservation.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void procesar_devolucion_returns_200_with_estado_procesada() throws Exception {
        String createResponse = mockMvc.perform(post("/api/devoluciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(devolucionBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long devId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(put("/api/devoluciones/" + devId + "/procesar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("procesada"))
                .andExpect(jsonPath("$.procesadaEn").exists());
    }

    @Test
    void rechazar_devolucion_returns_200_with_estado_rechazada() throws Exception {
        String createResponse = mockMvc.perform(post("/api/devoluciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(devolucionBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long devId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(put("/api/devoluciones/" + devId + "/rechazar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("rechazada"));
    }

    @Test
    void delete_pendiente_devolucion_returns_204() throws Exception {
        String createResponse = mockMvc.perform(post("/api/devoluciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(devolucionBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long devId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(delete("/api/devoluciones/" + devId))
                .andExpect(status().isNoContent());
    }
}
