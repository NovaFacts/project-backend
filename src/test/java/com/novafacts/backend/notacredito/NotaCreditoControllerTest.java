package com.novafacts.backend.notacredito;

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
class NotaCreditoControllerTest {

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

    private Factura savedFactura;

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
        canal.setNombre("Canal NC");
        canal.setTipo("Directo");
        Canal savedCanal = canalRepository.save(canal);

        Temporada temporada = new Temporada();
        temporada.setNombre("Temporada NC");
        temporada.setFechaInicio(LocalDate.of(2027, 1, 1));
        temporada.setFechaFin(LocalDate.of(2027, 12, 31));
        Temporada savedTemporada = temporadaRepository.save(temporada);

        Property property = new Property();
        property.setName("Casa NC Test");
        property.setAddress("Calle NC 1");
        Property savedProperty = propertyRepository.save(property);

        PoliticaCancelacion politica = new PoliticaCancelacion();
        politica.setPropiedad(savedProperty);
        politica.setNombre("Política NC");
        politica.setPorcentajeReembolso(new BigDecimal("0.00"));
        politica.setDiasAviso(0);
        PoliticaCancelacion savedPolitica = politicaRepository.save(politica);

        Reservation reservation = new Reservation();
        reservation.setCanal(savedCanal);
        reservation.setTemporada(savedTemporada);
        reservation.setPoliticaCancelacion(savedPolitica);
        reservation.setUsuarioCreador(savedUser);
        reservation.setPropertyId(savedProperty.getId());
        reservation.setClienteNombre("Ana Torres");
        reservation.setMontoTotal(new BigDecimal("500000.00"));
        reservation.setCheckIn(LocalDate.of(2027, 3, 1));
        reservation.setCheckOut(LocalDate.of(2027, 3, 5));
        reservation.setGuestCount(1);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        Reservation savedReservation = reservationRepository.save(reservation);

        // Create a factura directly via repository for setUp
        savedFactura = new Factura();
        savedFactura.setReserva(savedReservation);
        savedFactura.setUsuario(savedUser);
        savedFactura.setNumeroFactura("FAC-NC-TEST-001");
        savedFactura.setSubtotal(new BigDecimal("500000.00"));
        savedFactura.setDescuentoAnticipo(BigDecimal.ZERO);
        savedFactura.setRecargoPenalidad(BigDecimal.ZERO);
        savedFactura.setImpuestos(new BigDecimal("95000.00"));
        savedFactura.setTotal(new BigDecimal("595000.00"));
        savedFactura.setEstado(InvoiceStatus.PENDING);
        savedFactura = facturaRepository.save(savedFactura);
    }

    @Test
    void create_nota_credito_returns_201() throws Exception {
        String body = """
                {
                    "facturaId": %d,
                    "monto": 100000,
                    "motivo": "Descuento por fidelización"
                }
                """.formatted(savedFactura.getId());

        mockMvc.perform(post("/api/notas-credito")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.facturaId").value(savedFactura.getId()))
                .andExpect(jsonPath("$.monto").value(100000.0))
                .andExpect(jsonPath("$.motivo").value("Descuento por fidelización"))
                .andExpect(jsonPath("$.numeroNota").exists())
                .andExpect(jsonPath("$.usuarioNombre").value("Contador Test"));
    }

    @Test
    void create_nota_credito_with_missing_fields_returns_400() throws Exception {
        mockMvc.perform(post("/api/notas-credito")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"facturaId\": %d}".formatted(savedFactura.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_notas_by_factura_returns_list() throws Exception {
        String body = """
                {"facturaId": %d, "monto": 50000}
                """.formatted(savedFactura.getId());

        mockMvc.perform(post("/api/notas-credito")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/notas-credito/by-factura/" + savedFactura.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void delete_nota_credito_returns_204() throws Exception {
        String createResponse = mockMvc.perform(post("/api/notas-credito")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"facturaId\": %d, \"monto\": 50000}".formatted(savedFactura.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long ncId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(delete("/api/notas-credito/" + ncId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/notas-credito/" + ncId))
                .andExpect(status().isNotFound());
    }
}
