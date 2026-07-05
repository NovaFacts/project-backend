package com.novafacts.backend.dashboard;

import com.novafacts.backend.anticipo.entity.Anticipo;
import com.novafacts.backend.anticipo.entity.AnticipoEstado;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "ADMINISTRADOR")
class DashboardControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DevolucionRepository devolucionRepository;
    @Autowired private NotaCreditoRepository notaCreditoRepository;
    @Autowired private AnticipoRepository anticipoRepository;
    @Autowired private PenalidadRepository penalidadRepository;
    @Autowired private FacturaRepository facturaRepository;
    @Autowired private PropertyRepository propertyRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private PoliticaCancelacionRepository politicaRepository;
    @Autowired private CanalRepository canalRepository;
    @Autowired private TemporadaRepository temporadaRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RolRepository rolRepository;

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
        rol.setNombre("TestRol");
        Rol savedRol = rolRepository.save(rol);

        User user = new User();
        user.setUsername("test@dashboard.com");
        user.setPassword("$2a$10$irrelevant");
        user.setNombre("Dashboard Tester");
        user.setRol(savedRol);
        savedUser = userRepository.save(user);

        Canal canal = new Canal();
        canal.setNombre("Canal Dashboard");
        canal.setTipo("Plataforma");
        savedCanal = canalRepository.save(canal);

        Temporada temporada = new Temporada();
        temporada.setNombre("Temporada Dashboard");
        temporada.setFechaInicio(LocalDate.of(2027, 1, 1));
        temporada.setFechaFin(LocalDate.of(2027, 12, 31));
        savedTemporada = temporadaRepository.save(temporada);
    }

    @Test
    void getDashboard_emptyDatabase_returnsAllZeros() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGuests").value(0))
                .andExpect(jsonPath("$.totalProperties").value(0))
                .andExpect(jsonPath("$.confirmedReservations").value(0))
                .andExpect(jsonPath("$.cancelledReservations").value(0))
                .andExpect(jsonPath("$.completedReservations").value(0))
                .andExpect(jsonPath("$.pendingInvoices").value(0))
                .andExpect(jsonPath("$.paidInvoices").value(0))
                .andExpect(jsonPath("$.cancelledInvoices").value(0))
                .andExpect(jsonPath("$.totalAnticipos").value(0))
                .andExpect(jsonPath("$.montoTotalAnticipos").value(0));
    }

    @Test
    void getDashboard_withSeedData_returnsCorrectAggregates() throws Exception {
        // 2 properties
        for (int i = 1; i <= 2; i++) {
            Property p = new Property();
            p.setName("Propiedad " + i);
            p.setAddress("Calle " + i);
            propertyRepository.save(p);
        }

        Property prop = propertyRepository.findAll().get(0);

        PoliticaCancelacion politica = new PoliticaCancelacion();
        politica.setPropiedad(prop);
        politica.setNombre("Política Dashboard");
        politica.setPorcentajeReembolso(new BigDecimal("0.00"));
        politica.setDiasAviso(0);
        savedPolitica = politicaRepository.save(politica);

        // 4 reservations: 2 CONFIRMED, 1 CANCELLED, 1 COMPLETED
        Reservation r1 = saveReservation(prop.getId(), ReservationStatus.CONFIRMED,
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(12));
        Reservation r2 = saveReservation(prop.getId(), ReservationStatus.CONFIRMED,
                LocalDate.now().plusDays(20), LocalDate.now().plusDays(22));
        Reservation r3 = saveReservation(prop.getId(), ReservationStatus.CANCELLED,
                LocalDate.now().plusDays(30), LocalDate.now().plusDays(32));
        saveReservation(prop.getId(), ReservationStatus.COMPLETED,
                LocalDate.now().minusDays(5), LocalDate.now().minusDays(3));

        // 3 facturas using real reservation IDs (repo-level bypasses service duplicate check)
        facturaRepository.save(buildFactura(r1, InvoiceStatus.PENDING, "400000.00", "400000.00", "FAC-DASH-001"));
        facturaRepository.save(buildFactura(r2, InvoiceStatus.PAID,    "400000.00", "400000.00", "FAC-DASH-002"));
        facturaRepository.save(buildFactura(r3, InvoiceStatus.CANCELLED, "400000.00", "400000.00", "FAC-DASH-003"));

        // 1 anticipo linked to the first reservation
        Anticipo anticipo = new Anticipo();
        anticipo.setReserva(r1);
        anticipo.setUsuario(savedUser);
        anticipo.setMonto(new BigDecimal("476000.00"));
        anticipo.setFechaPago(LocalDate.now());
        anticipo.setEstado(AnticipoEstado.REGISTRADO);
        anticipoRepository.save(anticipo);

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGuests").value(4))
                .andExpect(jsonPath("$.totalProperties").value(2))
                .andExpect(jsonPath("$.confirmedReservations").value(2))
                .andExpect(jsonPath("$.cancelledReservations").value(1))
                .andExpect(jsonPath("$.completedReservations").value(1))
                .andExpect(jsonPath("$.pendingInvoices").value(1))
                .andExpect(jsonPath("$.paidInvoices").value(1))
                .andExpect(jsonPath("$.cancelledInvoices").value(1))
                .andExpect(jsonPath("$.totalAnticipos").value(1))
                .andExpect(jsonPath("$.montoTotalAnticipos").value(476000.00));
    }

    // M-14: dashboard exposes financial aggregates (invoice/anticipo figures), so it must
    // be restricted to the same roles as the anticipo/penalidad endpoints — verifying both
    // the positive (authorized roles still work) and negative (Recepcionista is blocked) sides.

    @Test
    @WithMockUser(roles = "CONTADOR")
    void getDashboard_asContador_returnsOk() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "AUXILIAR_CONTABLE")
    void getDashboard_asAuxiliarContable_returnsOk() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "RECEPCIONISTA")
    void getDashboard_asRecepcionista_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isForbidden());
    }

    private Reservation saveReservation(Long propertyId, ReservationStatus status,
                                        LocalDate checkIn, LocalDate checkOut) {
        Reservation r = new Reservation();
        r.setCanal(savedCanal);
        r.setTemporada(savedTemporada);
        r.setPoliticaCancelacion(savedPolitica);
        r.setUsuarioCreador(savedUser);
        r.setPropertyId(propertyId);
        r.setClienteNombre("Cliente Test");
        r.setMontoTotal(new BigDecimal("100000.00"));
        r.setCheckIn(checkIn);
        r.setCheckOut(checkOut);
        r.setGuestCount(2);
        r.setStatus(status);
        return reservationRepository.save(r);
    }

    private Factura buildFactura(Reservation reserva, InvoiceStatus estado,
                                 String subtotal, String total, String numero) {
        Factura f = new Factura();
        f.setReserva(reserva);
        f.setUsuario(savedUser);
        f.setNumeroFactura(numero);
        f.setSubtotal(new BigDecimal(subtotal));
        f.setDescuentoAnticipo(BigDecimal.ZERO);
        f.setRecargoPenalidad(BigDecimal.ZERO);
        f.setImpuestos(BigDecimal.ZERO);
        f.setTotal(new BigDecimal(total));
        f.setEstado(estado);
        return f;
    }
}
