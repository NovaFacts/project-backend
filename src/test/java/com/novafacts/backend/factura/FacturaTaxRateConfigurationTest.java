package com.novafacts.backend.factura;

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
import com.novafacts.backend.anticipo.repository.AnticipoRepository;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4 / Phase 5 (Configuration Validation): proves app.iva-rate is genuinely
 * policy-driven, not hardcoded — overriding it to 0.18 here (instead of the project
 * default 0.19) must change FacturaService.create()'s computed impuestos with zero
 * code change, confirming the property actually reaches the injected @Value field.
 * A separate top-level class (rather than a @Nested class inside FacturaControllerTest)
 * keeps @TestPropertySource scoped to only this test, leaving every other Factura test
 * running against the real default rate.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.iva-rate=0.18")
@WithMockUser(username = "contador@test.com", roles = {"CONTADOR"})
class FacturaTaxRateConfigurationTest {

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

        User user = new User();
        user.setUsername("contador@test.com");
        user.setPassword("$2a$10$irrelevant");
        user.setNombre("Contador Test");
        user.setRol(savedRol);
        User savedUser = userRepository.save(user);

        Canal canal = new Canal();
        canal.setNombre("Canal Tasa Impuesto");
        canal.setTipo("Directo");
        Canal savedCanal = canalRepository.save(canal);

        Temporada temporada = new Temporada();
        temporada.setNombre("Temporada Tasa Impuesto");
        temporada.setFechaInicio(LocalDate.of(2027, 1, 1));
        temporada.setFechaFin(LocalDate.of(2027, 12, 31));
        Temporada savedTemporada = temporadaRepository.save(temporada);

        Property property = new Property();
        property.setName("Casa Tasa Impuesto Test");
        property.setAddress("Calle Tasa Impuesto 1");
        Property savedProperty = propertyRepository.save(property);

        PoliticaCancelacion politica = new PoliticaCancelacion();
        politica.setPropiedad(savedProperty);
        politica.setNombre("Política Tasa Impuesto");
        politica.setPorcentajeReembolso(new BigDecimal("0.00"));
        politica.setDiasAviso(0);
        PoliticaCancelacion savedPolitica = politicaRepository.save(politica);

        Reservation reservation = new Reservation();
        reservation.setCanal(savedCanal);
        reservation.setTemporada(savedTemporada);
        reservation.setPoliticaCancelacion(savedPolitica);
        reservation.setUsuarioCreador(savedUser);
        reservation.setPropertyId(savedProperty.getId());
        reservation.setClienteNombre("Cliente Tasa Impuesto");
        reservation.setMontoTotal(new BigDecimal("1000000.00"));
        reservation.setCheckIn(LocalDate.of(2027, 2, 1));
        reservation.setCheckOut(LocalDate.of(2027, 2, 6));
        reservation.setGuestCount(2);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        savedReservation = reservationRepository.save(reservation);
    }

    @Test
    void create_factura_uses_configured_rate_instead_of_hardcoded_default() throws Exception {
        // With app.iva-rate overridden to 0.18 (not the project default 0.19):
        // base = 1000000; impuestos = 1000000 x 0.18 = 180000; total = 1180000.
        // No code change was needed to produce this — only the property value differs.
        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservaId\": %d}".formatted(savedReservation.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.impuestos").value(180000.0))
                .andExpect(jsonPath("$.total").value(1180000.0));
    }
}
