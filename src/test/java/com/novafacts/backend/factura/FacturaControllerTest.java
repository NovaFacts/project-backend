package com.novafacts.backend.factura;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.novafacts.backend.penalidad.entity.Penalidad;
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
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                    "reservaId": %d
                }
                """.formatted(savedReservation.getId());
    }

    /**
     * Saved directly via the repository, bypassing PenalidadService.create() —
     * that service requires the reservation to be CANCELLED, which would in turn
     * make FacturaService.create() reject it as "No se puede facturar una reserva
     * cancelada". A reservation genuinely can't reach both states through the real
     * API today; that pre-existing tension is out of scope for this phase (see the
     * report's Remaining Gaps). Building the fixture directly, exactly like
     * saveRegisteredAnticipo() above, is the established pattern elsewhere in this
     * suite for constructing cross-cutting states that the service layer itself
     * wouldn't allow end-to-end.
     */
    private Penalidad savePenalidad(Reservation reservation, BigDecimal montoAprobado) {
        Penalidad penalidad = new Penalidad();
        penalidad.setReserva(reservation);
        penalidad.setUsuario(savedUser);
        penalidad.setMontoSegunPolitica(montoAprobado);
        penalidad.setMontoAprobado(montoAprobado);
        penalidad.setFechaCancelacion(LocalDate.of(2027, 1, 15));
        penalidad.setMotivo("Cancelación tardía");
        return penalidadRepository.save(penalidad);
    }

    private Anticipo saveRegisteredAnticipo(Reservation reservation, BigDecimal monto) {
        Anticipo anticipo = new Anticipo();
        anticipo.setReserva(reservation);
        anticipo.setUsuario(savedUser);
        anticipo.setMonto(monto);
        anticipo.setFechaPago(LocalDate.of(2027, 1, 20));
        anticipo.setMetodoPago("transferencia");
        anticipo.setEstado(AnticipoEstado.REGISTRADO);
        return anticipoRepository.save(anticipo);
    }

    // ── RF11/RF12: server-side anticipo aggregation ─────────────────────────────

    @Test
    void create_factura_with_no_registered_anticipos_has_zero_descuento() throws Exception {
        // total = 1000000 - 0 + 0 + 190000 = 1190000
        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(facturaBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDING"))
                .andExpect(jsonPath("$.subtotal").value(1000000.0))
                .andExpect(jsonPath("$.descuentoAnticipo").value(0.0))
                .andExpect(jsonPath("$.recargoPenalidad").value(0.0))
                .andExpect(jsonPath("$.impuestos").value(190000.0))
                .andExpect(jsonPath("$.total").value(1190000.0))
                .andExpect(jsonPath("$.reservaId").value(savedReservation.getId()))
                .andExpect(jsonPath("$.usuarioNombre").value("Contador Test"))
                .andExpect(jsonPath("$.numeroFactura").exists());
    }

    @Test
    void create_factura_applies_single_registered_anticipo_and_marks_it_aplicado() throws Exception {
        Anticipo anticipo = saveRegisteredAnticipo(savedReservation, new BigDecimal("200000.00"));

        // base = 1000000 - 200000 + 0 = 800000; impuestos = 800000 x 0.19 = 152000; total = 952000
        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(facturaBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.descuentoAnticipo").value(200000.0))
                .andExpect(jsonPath("$.impuestos").value(152000.0))
                .andExpect(jsonPath("$.total").value(952000.0));

        Anticipo persisted = anticipoRepository.findById(anticipo.getId()).orElseThrow();
        assertEquals(AnticipoEstado.APLICADO, persisted.getEstado());
    }

    @Test
    void create_factura_applies_multiple_registered_anticipos_and_marks_all_aplicado() throws Exception {
        Anticipo a1 = saveRegisteredAnticipo(savedReservation, new BigDecimal("150000.00"));
        Anticipo a2 = saveRegisteredAnticipo(savedReservation, new BigDecimal("100000.00"));
        Anticipo a3 = saveRegisteredAnticipo(savedReservation, new BigDecimal("50000.00"));

        // descuentoAnticipo = 150000 + 100000 + 50000 = 300000
        // base = 1000000 - 300000 = 700000; impuestos = 700000 x 0.19 = 133000; total = 833000
        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(facturaBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.descuentoAnticipo").value(300000.0))
                .andExpect(jsonPath("$.impuestos").value(133000.0))
                .andExpect(jsonPath("$.total").value(833000.0));

        for (Long id : new Long[] { a1.getId(), a2.getId(), a3.getId() }) {
            Anticipo persisted = anticipoRepository.findById(id).orElseThrow();
            assertEquals(AnticipoEstado.APLICADO, persisted.getEstado());
        }
    }

    @Test
    void create_factura_ignores_client_supplied_descuentoAnticipo_and_anticipoId() throws Exception {
        // The real, registered anticipo is only 50000 — a crafted request tries to claim
        // a much larger discount (and references a nonexistent anticipoId) directly in
        // the JSON body. Both fields no longer exist on FacturaRequest, so Jackson drops
        // them silently; the server must derive the real value regardless of what's sent.
        Anticipo anticipo = saveRegisteredAnticipo(savedReservation, new BigDecimal("50000.00"));

        String craftedBody = """
                {
                    "reservaId": %d,
                    "descuentoAnticipo": 999999999,
                    "anticipoId": 999999,
                    "recargoPenalidad": 0,
                    "impuestos": 1
                }
                """.formatted(savedReservation.getId());

        // base = 1000000 - 50000 = 950000; impuestos = 950000 x 0.19 = 180500 (never the
        // crafted 1); total = 1130500 (never using the crafted 999999999 discount either)
        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(craftedBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.descuentoAnticipo").value(50000.0))
                .andExpect(jsonPath("$.impuestos").value(180500.0))
                .andExpect(jsonPath("$.total").value(1130500.0));

        Anticipo persisted = anticipoRepository.findById(anticipo.getId()).orElseThrow();
        assertEquals(AnticipoEstado.APLICADO, persisted.getEstado());
    }

    @Test
    void create_factura_only_applies_anticipos_for_its_own_reservation() throws Exception {
        Anticipo ownAnticipo = saveRegisteredAnticipo(savedReservation, new BigDecimal("80000.00"));

        Reservation otherReservation = new Reservation();
        otherReservation.setCanal(savedReservation.getCanal());
        otherReservation.setTemporada(savedReservation.getTemporada());
        otherReservation.setPoliticaCancelacion(savedReservation.getPoliticaCancelacion());
        otherReservation.setUsuarioCreador(savedUser);
        otherReservation.setPropertyId(savedReservation.getPropertyId());
        otherReservation.setClienteNombre("Otro Cliente");
        otherReservation.setMontoTotal(new BigDecimal("500000.00"));
        otherReservation.setCheckIn(LocalDate.of(2027, 3, 1));
        otherReservation.setCheckOut(LocalDate.of(2027, 3, 5));
        otherReservation.setGuestCount(1);
        otherReservation.setStatus(ReservationStatus.CONFIRMED);
        otherReservation = reservationRepository.save(otherReservation);
        Anticipo otherAnticipo = saveRegisteredAnticipo(otherReservation, new BigDecimal("999000.00"));

        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(facturaBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.descuentoAnticipo").value(80000.0));

        assertEquals(AnticipoEstado.APLICADO, anticipoRepository.findById(ownAnticipo.getId()).orElseThrow().getEstado());
        assertEquals(AnticipoEstado.REGISTRADO, anticipoRepository.findById(otherAnticipo.getId()).orElseThrow().getEstado());
    }

    @Test
    void create_factura_for_already_invoiced_reservation_leaves_new_anticipo_untouched() throws Exception {
        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(facturaBody()))
                .andExpect(status().isCreated());

        // Registered only after the reservation is already invoiced.
        Anticipo lateAnticipo = saveRegisteredAnticipo(savedReservation, new BigDecimal("999999.00"));

        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(facturaBody()))
                .andExpect(status().isConflict());

        // The rejected second attempt must not have touched any anticipo.
        assertEquals(AnticipoEstado.REGISTRADO, anticipoRepository.findById(lateAnticipo.getId()).orElseThrow().getEstado());
        assertEquals(1, facturaRepository.count());
    }

    // ── Penalidad aggregation (Phase 2) ─────────────────────────────────────────

    @Test
    void create_factura_with_no_penalidad_has_zero_recargo() throws Exception {
        // total = 1000000 - 0 + 0 + 190000 = 1190000
        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(facturaBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recargoPenalidad").value(0.0))
                .andExpect(jsonPath("$.total").value(1190000.0));
    }

    @Test
    void create_factura_applies_approved_penalidad_amount() throws Exception {
        savePenalidad(savedReservation, new BigDecimal("75000.00"));

        // base = 1000000 + 75000 = 1075000; impuestos = 1075000 x 0.19 = 204250; total = 1279250
        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(facturaBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recargoPenalidad").value(75000.0))
                .andExpect(jsonPath("$.impuestos").value(204250.0))
                .andExpect(jsonPath("$.total").value(1279250.0));
    }

    @Test
    void create_factura_ignores_client_supplied_recargoPenalidad() throws Exception {
        // Real approved penalidad is only 75000 — a crafted request tries to claim a much
        // larger surcharge directly in the JSON body. The field no longer exists on
        // FacturaRequest, so Jackson drops it; the server must derive the real value.
        savePenalidad(savedReservation, new BigDecimal("75000.00"));

        String craftedBody = """
                {
                    "reservaId": %d,
                    "recargoPenalidad": 999999999,
                    "impuestos": -999999
                }
                """.formatted(savedReservation.getId());

        // base = 1000000 + 75000 = 1075000; impuestos = 1075000 x 0.19 = 204250 (never the
        // crafted -999999); total = 1279250 (never using the crafted 999999999 surcharge)
        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(craftedBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recargoPenalidad").value(75000.0))
                .andExpect(jsonPath("$.impuestos").value(204250.0))
                .andExpect(jsonPath("$.total").value(1279250.0));
    }

    @Test
    void create_factura_only_applies_penalidad_for_its_own_reservation() throws Exception {
        Reservation otherReservation = new Reservation();
        otherReservation.setCanal(savedReservation.getCanal());
        otherReservation.setTemporada(savedReservation.getTemporada());
        otherReservation.setPoliticaCancelacion(savedReservation.getPoliticaCancelacion());
        otherReservation.setUsuarioCreador(savedUser);
        otherReservation.setPropertyId(savedReservation.getPropertyId());
        otherReservation.setClienteNombre("Otro Cliente Penalidad");
        otherReservation.setMontoTotal(new BigDecimal("500000.00"));
        otherReservation.setCheckIn(LocalDate.of(2027, 4, 1));
        otherReservation.setCheckOut(LocalDate.of(2027, 4, 5));
        otherReservation.setGuestCount(1);
        otherReservation.setStatus(ReservationStatus.CONFIRMED);
        otherReservation = reservationRepository.save(otherReservation);
        savePenalidad(otherReservation, new BigDecimal("300000.00"));

        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(facturaBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recargoPenalidad").value(0.0));
    }

    @Test
    void create_factura_for_already_invoiced_reservation_leaves_penalidad_untouched() throws Exception {
        Penalidad penalidad = savePenalidad(savedReservation, new BigDecimal("50000.00"));

        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(facturaBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recargoPenalidad").value(50000.0));

        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(facturaBody()))
                .andExpect(status().isConflict());

        // Penalidad is immutable and untouched either way, but the rejected second
        // attempt must still not have created a second Factura.
        Penalidad persisted = penalidadRepository.findById(penalidad.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("50000.00").compareTo(persisted.getMontoAprobado()));
        assertEquals(1, facturaRepository.count());
    }

    // ── subtotal authority (Phase 3) ─────────────────────────────────────────────

    @Test
    void create_factura_without_subtotal_in_body_still_succeeds_using_reservation_amount() throws Exception {
        // subtotal is no longer a request field at all — omitting it entirely (not even
        // sending the key) must not trigger any validation error, since reservaId is the
        // only field FacturaRequest still requires.
        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservaId\": %d}".formatted(savedReservation.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(1000000.0));
    }

    @Test
    void create_factura_subtotal_always_equals_reservation_montoTotal_regardless_of_crafted_value() throws Exception {
        // savedReservation.montoTotal = 1000000.00 (set in setUp()). Each crafted request
        // below tries a different forged subtotal; none of them exist on FacturaRequest
        // anymore, so Jackson drops the field and every one must resolve to the exact
        // same, real, persisted amount.
        String[] craftedSubtotals = { "999999999", "1", "-500000", "0" };

        for (String crafted : craftedSubtotals) {
            // A distinct reservation per crafted value — Factura has a unique constraint
            // on reserva_id, so each attempt needs its own reservation, not reuse/deletion.
            Reservation reservation = new Reservation();
            reservation.setCanal(savedReservation.getCanal());
            reservation.setTemporada(savedReservation.getTemporada());
            reservation.setPoliticaCancelacion(savedReservation.getPoliticaCancelacion());
            reservation.setUsuarioCreador(savedUser);
            reservation.setPropertyId(savedReservation.getPropertyId());
            reservation.setClienteNombre("Cliente Forjado " + crafted);
            reservation.setMontoTotal(new BigDecimal("1000000.00"));
            reservation.setCheckIn(LocalDate.of(2027, 5, 1));
            reservation.setCheckOut(LocalDate.of(2027, 5, 5));
            reservation.setGuestCount(1);
            reservation.setStatus(ReservationStatus.CONFIRMED);
            Reservation saved = reservationRepository.save(reservation);

            String craftedBody = """
                    {
                        "reservaId": %d,
                        "subtotal": %s,
                        "impuestos": 0
                    }
                    """.formatted(saved.getId(), crafted);

            mockMvc.perform(post("/api/facturas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(craftedBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.subtotal").value(1000000.0));
        }
    }

    // ── tax authority (Phase 4) ───────────────────────────────────────────────────

    @Test
    void create_factura_computes_impuestos_using_default_configured_rate() throws Exception {
        // No anticipo, no penalidad: base = 1000000; impuestos = 1000000 x 0.19 (app.iva-rate
        // default) = 190000; total = 1190000. Named explicitly to document the default-rate
        // case, distinct from FacturaTaxRateConfigurationTest's overridden-rate case.
        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(facturaBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.impuestos").value(190000.0))
                .andExpect(jsonPath("$.total").value(1190000.0));
    }

    @Test
    void create_factura_impuestos_always_equals_base_times_rate_regardless_of_crafted_value() throws Exception {
        // savedReservation.montoTotal = 1000000.00, no anticipo/penalidad, so base = 1000000
        // and the real impuestos is always 190000 (1000000 x 0.19). impuestos no longer
        // exists on FacturaRequest, so every crafted value below must be silently dropped.
        String[] craftedImpuestos = { "999999999", "1", "0", "-50000" };

        for (String crafted : craftedImpuestos) {
            Reservation reservation = new Reservation();
            reservation.setCanal(savedReservation.getCanal());
            reservation.setTemporada(savedReservation.getTemporada());
            reservation.setPoliticaCancelacion(savedReservation.getPoliticaCancelacion());
            reservation.setUsuarioCreador(savedUser);
            reservation.setPropertyId(savedReservation.getPropertyId());
            reservation.setClienteNombre("Cliente Impuesto Forjado " + crafted);
            reservation.setMontoTotal(new BigDecimal("1000000.00"));
            reservation.setCheckIn(LocalDate.of(2027, 6, 1));
            reservation.setCheckOut(LocalDate.of(2027, 6, 5));
            reservation.setGuestCount(1);
            reservation.setStatus(ReservationStatus.CONFIRMED);
            Reservation saved = reservationRepository.save(reservation);

            String craftedBody = """
                    {
                        "reservaId": %d,
                        "impuestos": %s
                    }
                    """.formatted(saved.getId(), crafted);

            mockMvc.perform(post("/api/facturas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(craftedBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.impuestos").value(190000.0))
                    .andExpect(jsonPath("$.total").value(1190000.0));
        }
    }

    @Test
    void create_factura_with_zero_tax_base_has_zero_impuestos() throws Exception {
        // Anticipo discount exactly cancels out the subtotal (no penalidad), so base = 0
        // and impuestos = 0 x 0.19 = 0 — not client-suppliable, and not negative either.
        saveRegisteredAnticipo(savedReservation, new BigDecimal("1000000.00"));

        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(facturaBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.descuentoAnticipo").value(1000000.0))
                .andExpect(jsonPath("$.impuestos").value(0.0))
                .andExpect(jsonPath("$.total").value(0.0));
    }

    @Test
    void create_factura_for_nonexistent_reservation_returns_404() throws Exception {
        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservaId\": 99999}"))
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
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].estado").value("PENDING"));
    }

    // ── emitir() — previously untested; added to close the coverage gap ────────

    private Long createPendingFactura() throws Exception {
        String createResponse = mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(facturaBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(createResponse).get("id").asLong();
    }

    @Test
    void emitir_pending_factura_returns_200_with_paid_status() throws Exception {
        Long facturaId = createPendingFactura();

        mockMvc.perform(put("/api/facturas/" + facturaId + "/emitir"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(facturaId))
                .andExpect(jsonPath("$.estado").value("PAID"))
                .andExpect(jsonPath("$.total").value(1190000.0));

        // Verify the persisted state directly, not just the HTTP response.
        Factura persisted = facturaRepository.findById(facturaId).orElseThrow();
        assertEquals(InvoiceStatus.PAID, persisted.getEstado());
    }

    @Test
    void emitir_already_emitida_factura_returns_409_and_second_request_is_rejected() throws Exception {
        // Also exercises the idempotency/regression requirement: the first emitir()
        // succeeds, and a repeated request against the now-PAID invoice is rejected
        // by the existing business validation rather than silently re-applying.
        Long facturaId = createPendingFactura();

        mockMvc.perform(put("/api/facturas/" + facturaId + "/emitir"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PAID"));

        mockMvc.perform(put("/api/facturas/" + facturaId + "/emitir"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Solo se pueden emitir facturas en estado pendiente"));

        Factura persisted = facturaRepository.findById(facturaId).orElseThrow();
        assertEquals(InvoiceStatus.PAID, persisted.getEstado());
    }

    @Test
    void emitir_cancelled_factura_returns_409() throws Exception {
        Long facturaId = createPendingFactura();

        mockMvc.perform(put("/api/facturas/" + facturaId + "/anular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELLED"));

        mockMvc.perform(put("/api/facturas/" + facturaId + "/emitir"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Solo se pueden emitir facturas en estado pendiente"));

        Factura persisted = facturaRepository.findById(facturaId).orElseThrow();
        assertEquals(InvoiceStatus.CANCELLED, persisted.getEstado());
    }

    @Test
    void emitir_nonexistent_factura_returns_404() throws Exception {
        mockMvc.perform(put("/api/facturas/999999/emitir"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Factura no encontrada"));
    }

    @Test
    @WithAnonymousUser
    void emitir_unauthenticated_returns_401() throws Exception {
        // Overrides the class-level @WithMockUser for this one test to simulate a
        // request carrying no authentication at all.
        mockMvc.perform(put("/api/facturas/999999/emitir"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "auxiliar@test.com", roles = {"AUXILIAR_CONTABLE"})
    void emitir_without_required_role_returns_403() throws Exception {
        // AUXILIAR_CONTABLE can read/write anticipos and penalidades but is
        // deliberately excluded from facturas by SecurityConfig — the actual RBAC
        // boundary this endpoint enforces, not just an arbitrary unrelated role.
        mockMvc.perform(put("/api/facturas/999999/emitir"))
                .andExpect(status().isForbidden());
    }
}
