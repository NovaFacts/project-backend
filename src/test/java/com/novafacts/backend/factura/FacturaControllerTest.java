package com.novafacts.backend.factura;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novafacts.backend.anticipo.repository.AnticipoRepository;
import com.novafacts.backend.auth.entity.User;
import com.novafacts.backend.auth.repository.UserRepository;
import com.novafacts.backend.canal.entity.Canal;
import com.novafacts.backend.canal.repository.CanalRepository;
import com.novafacts.backend.devolucion.repository.DevolucionRepository;
import com.novafacts.backend.factura.entity.Factura;
import com.novafacts.backend.factura.repository.FacturaRepository;
import com.novafacts.backend.invoice.entity.InvoiceStatus;
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
class FacturaControllerTest {

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

        Canal canal = new Canal();
        canal.setNombre("Canal Factura");
        canal.setTipo("Directo");
        Canal savedCanal = canalRepository.save(canal);

        Temporada temporada = new Temporada();
        temporada.setNombre("Temporada Factura");
        temporada.setFechaInicio(LocalDate.of(2027, 1, 1));
        temporada.setFechaFin(LocalDate.of(2027, 12, 31));
        Temporada savedTemporada = temporadaRepository.save(temporada);

        Property property = new Property();
        property.setName("Casa Factura Test");
        property.setAddress("Calle Factura 1");
        Property savedProperty = propertyRepository.save(property);

        PoliticaCancelacion politica = new PoliticaCancelacion();
        politica.setPropiedad(savedProperty);
        politica.setNombre("Política Factura");
        politica.setPorcentajeReembolso(new BigDecimal("0.00"));
        politica.setDiasAviso(0);
        PoliticaCancelacion savedPolitica = politicaRepository.save(politica);

        Reservation reservation = new Reservation();
        reservation.setCanal(savedCanal);
        reservation.setTemporada(savedTemporada);
        reservation.setPoliticaCancelacion(savedPolitica);
        reservation.setUsuarioCreador(savedUser);
        reservation.setPropertyId(savedProperty.getId());
        reservation.setClienteNombre("Carlos López");
        reservation.setMontoTotal(new BigDecimal("1000000.00"));
        reservation.setCheckIn(LocalDate.of(2027, 2, 1));
        reservation.setCheckOut(LocalDate.of(2027, 2, 6));
        reservation.setGuestCount(2);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        savedReservation = reservationRepository.save(reservation);
    }

    private String facturaBody() {
        return """
                {
                    "reservaId": %d,
                    "subtotal": 1000000,
                    "descuentoAnticipo": 200000,
                    "recargoPenalidad": 0,
                    "impuestos": 190000
                }
                """.formatted(savedReservation.getId());
    }

    @Test
    void create_factura_returns_201_with_calculated_total() throws Exception {
        // total = 1000000 - 200000 + 0 + 190000 = 990000
        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(facturaBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDING"))
                .andExpect(jsonPath("$.subtotal").value(1000000.0))
                .andExpect(jsonPath("$.descuentoAnticipo").value(200000.0))
                .andExpect(jsonPath("$.recargoPenalidad").value(0.0))
                .andExpect(jsonPath("$.impuestos").value(190000.0))
                .andExpect(jsonPath("$.total").value(990000.0))
                .andExpect(jsonPath("$.reservaId").value(savedReservation.getId()))
                .andExpect(jsonPath("$.usuarioNombre").value("Contador Test"))
                .andExpect(jsonPath("$.numeroFactura").exists());
    }

    @Test
    void create_factura_with_missing_subtotal_returns_400() throws Exception {
        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservaId\": %d}".formatted(savedReservation.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_factura_for_nonexistent_reservation_returns_404() throws Exception {
        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservaId\": 99999, \"subtotal\": 500000}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicate_factura_returns_409() throws Exception {
        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(facturaBody()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(facturaBody()))
                .andExpect(status().isConflict());
    }

    @Test
    void get_factura_by_id_returns_200() throws Exception {
        String createResponse = mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(facturaBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long facturaId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(get("/api/facturas/" + facturaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(facturaId))
                .andExpect(jsonPath("$.estado").value("PENDING"));
    }

    @Test
    void get_factura_by_reserva_returns_200() throws Exception {
        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(facturaBody()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/facturas/by-reserva/" + savedReservation.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservaId").value(savedReservation.getId()))
                .andExpect(jsonPath("$.estado").value("PENDING"));
    }

    @Test
    void anular_factura_returns_200_with_cancelled_status() throws Exception {
        String createResponse = mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(facturaBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long facturaId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(put("/api/facturas/" + facturaId + "/anular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELLED"));
    }

    @Test
    void delete_emitida_factura_returns_409() throws Exception {
        String createResponse = mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(facturaBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long facturaId = objectMapper.readTree(createResponse).get("id").asLong();

        // Directly mark as PAID via repository to simulate emitida state
        Factura factura = facturaRepository.findById(facturaId).orElseThrow();
        factura.setEstado(InvoiceStatus.PAID);
        facturaRepository.save(factura);

        mockMvc.perform(delete("/api/facturas/" + facturaId))
                .andExpect(status().isConflict());
    }

    @Test
    void get_all_facturas_returns_list() throws Exception {
        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(facturaBody()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/facturas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
