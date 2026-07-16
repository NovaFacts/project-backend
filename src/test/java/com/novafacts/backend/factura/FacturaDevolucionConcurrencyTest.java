package com.novafacts.backend.factura;

import com.novafacts.backend.anticipo.entity.Anticipo;
import com.novafacts.backend.anticipo.entity.AnticipoEstado;
import com.novafacts.backend.anticipo.repository.AnticipoRepository;
import com.novafacts.backend.auth.entity.User;
import com.novafacts.backend.auth.repository.UserRepository;
import com.novafacts.backend.canal.entity.Canal;
import com.novafacts.backend.canal.repository.CanalRepository;
import com.novafacts.backend.devolucion.entity.Devolucion;
import com.novafacts.backend.devolucion.entity.DevolucionEstado;
import com.novafacts.backend.devolucion.repository.DevolucionRepository;
import com.novafacts.backend.devolucion.service.DevolucionService;
import com.novafacts.backend.factura.entity.Factura;
import com.novafacts.backend.factura.repository.FacturaRepository;
import com.novafacts.backend.factura.service.FacturaService;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C-2 (AUDIT_v5): proves FacturaRepository.findByIdForUpdate() and
 * DevolucionRepository.findByIdForUpdate()'s pessimistic locks prevent two
 * concurrent state-transition requests on the same invoice/refund from both
 * succeeding with contradictory outcomes. Before this fix, two concurrent
 * PUT /api/facturas/{id}/emitir and /anular calls (or the Devolucion equivalents)
 * both returned 200 with mutually-exclusive responses, and the database silently
 * reflected only whichever transaction committed last — reproduced live during the
 * original C-2 investigation and again during the AUDIT_v5 post-remediation
 * reassessment.
 *
 * Runs against a real Testcontainers PostgreSQL instance so the SELECT ... FOR
 * UPDATE locks genuinely serialize the concurrent transactions, mirroring
 * AnticipoConcurrencyTest/ReservationConcurrencyTest's structure exactly:
 * CountDownLatch-synchronized start, a shared SecurityContext propagated to worker
 * threads, real service calls (no mocking), no Thread.sleep-based synchronization.
 */
@SpringBootTest
@ActiveProfiles("test")
class FacturaDevolucionConcurrencyTest {

    @Autowired private FacturaService facturaService;
    @Autowired private DevolucionService devolucionService;
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

    private Canal savedCanal;
    private Temporada savedTemporada;
    private PoliticaCancelacion savedPolitica;
    private User savedUser;
    private Property savedProperty;

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
        savedCanal.setNombre("Canal Concurrencia Factura");
        savedCanal.setTipo("Directo");
        savedCanal = canalRepository.save(savedCanal);

        savedTemporada = new Temporada();
        savedTemporada.setNombre("Temporada Concurrencia Factura");
        savedTemporada.setFechaInicio(LocalDate.of(2027, 1, 1));
        savedTemporada.setFechaFin(LocalDate.of(2027, 12, 31));
        savedTemporada = temporadaRepository.save(savedTemporada);

        savedProperty = new Property();
        savedProperty.setName("Finca Concurrencia Factura Test");
        savedProperty.setAddress("Vereda La Esperanza");
        savedProperty = propertyRepository.save(savedProperty);

        savedPolitica = new PoliticaCancelacion();
        savedPolitica.setPropiedad(savedProperty);
        savedPolitica.setNombre("Política Concurrencia Factura");
        savedPolitica.setPorcentajeReembolso(new BigDecimal("50.00"));
        savedPolitica.setDiasAviso(3);
        savedPolitica = politicaRepository.save(savedPolitica);
    }

    private Reservation newReservation(String clienteNombre) {
        Reservation reservation = new Reservation();
        reservation.setCanal(savedCanal);
        reservation.setTemporada(savedTemporada);
        reservation.setPoliticaCancelacion(savedPolitica);
        reservation.setUsuarioCreador(savedUser);
        reservation.setPropertyId(savedProperty.getId());
        reservation.setClienteNombre(clienteNombre);
        reservation.setMontoTotal(new BigDecimal("900000.00"));
        reservation.setCheckIn(LocalDate.of(2027, 6, 1));
        reservation.setCheckOut(LocalDate.of(2027, 6, 5));
        reservation.setGuestCount(2);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        return reservationRepository.save(reservation);
    }

    private Factura savePendingFactura(Reservation reservation) {
        Factura factura = new Factura();
        factura.setReserva(reservation);
        factura.setUsuario(savedUser);
        factura.setNumeroFactura("FAC-CONC-" + reservation.getId());
        factura.setSubtotal(new BigDecimal("900000.00"));
        factura.setDescuentoAnticipo(BigDecimal.ZERO);
        factura.setRecargoPenalidad(BigDecimal.ZERO);
        factura.setImpuestos(BigDecimal.ZERO);
        factura.setTotal(new BigDecimal("900000.00"));
        factura.setEstado(InvoiceStatus.PENDING);
        return facturaRepository.save(factura);
    }

    private Anticipo saveRegisteredAnticipo(Reservation reservation) {
        Anticipo anticipo = new Anticipo();
        anticipo.setReserva(reservation);
        anticipo.setUsuario(savedUser);
        anticipo.setMonto(new BigDecimal("300000.00"));
        anticipo.setFechaPago(LocalDate.of(2027, 5, 15));
        anticipo.setMetodoPago("transferencia");
        anticipo.setEstado(AnticipoEstado.REGISTRADO);
        return anticipoRepository.save(anticipo);
    }

    private Devolucion savePendingDevolucion(Reservation reservation, Anticipo anticipo) {
        Devolucion devolucion = new Devolucion();
        devolucion.setReserva(reservation);
        devolucion.setAnticipo(anticipo);
        devolucion.setUsuario(savedUser);
        devolucion.setMonto(new BigDecimal("300000.00"));
        devolucion.setMetodo("transferencia");
        devolucion.setEstado(DevolucionEstado.PENDIENTE);
        return devolucionRepository.save(devolucion);
    }

    /**
     * Runs two callables concurrently, released together via a CountDownLatch once
     * both are parked waiting to start, so they race as close to simultaneously as
     * possible — no Thread.sleep anywhere. Returns their results in submission order.
     */
    private Object[] runConcurrently(Callable<Object> first, Callable<Object> second) throws Exception {
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        SecurityContext sharedContext = SecurityContextHolder.getContext();

        Callable<Object> wrappedFirst = () -> {
            SecurityContextHolder.setContext(sharedContext);
            readyLatch.countDown();
            startLatch.await();
            try {
                return first.call();
            } catch (ResponseStatusException e) {
                return e;
            }
        };
        Callable<Object> wrappedSecond = () -> {
            SecurityContextHolder.setContext(sharedContext);
            readyLatch.countDown();
            startLatch.await();
            try {
                return second.call();
            } catch (ResponseStatusException e) {
                return e;
            }
        };

        Future<Object> firstFuture = executor.submit(wrappedFirst);
        Future<Object> secondFuture = executor.submit(wrappedSecond);

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        Object firstResult = firstFuture.get(10, TimeUnit.SECONDS);
        Object secondResult = secondFuture.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        return new Object[] { firstResult, secondResult };
    }

    private void assertExactlyOneSucceeded(Object firstResult, Object secondResult,
                                            String firstLabel, String secondLabel) {
        boolean firstSucceeded = !(firstResult instanceof ResponseStatusException);
        boolean secondSucceeded = !(secondResult instanceof ResponseStatusException);

        assertTrue(firstSucceeded ^ secondSucceeded,
                "Expected exactly one of " + firstLabel + "/" + secondLabel + " to succeed, but "
                        + firstLabel + "=" + firstSucceeded + " " + secondLabel + "=" + secondSucceeded);

        if (!firstSucceeded) {
            assertInstanceOf(ResponseStatusException.class, firstResult);
            assertEquals(HttpStatus.CONFLICT, ((ResponseStatusException) firstResult).getStatusCode());
        }
        if (!secondSucceeded) {
            assertInstanceOf(ResponseStatusException.class, secondResult);
            assertEquals(HttpStatus.CONFLICT, ((ResponseStatusException) secondResult).getStatusCode());
        }
    }

    @Test
    @WithMockUser(username = "contador@test.com", roles = {"CONTADOR"})
    void concurrent_emitir_and_anular_on_same_factura_only_one_succeeds() throws Exception {
        Reservation reservation = newReservation("Cliente Factura Concurrencia");
        Factura factura = savePendingFactura(reservation);
        Long facturaId = factura.getId();

        Callable<Object> emitirTask = () -> facturaService.emitir(facturaId);
        Callable<Object> anularTask = () -> facturaService.anular(facturaId);

        Object[] results = runConcurrently(emitirTask, anularTask);
        Object emitirResult = results[0];
        Object anularResult = results[1];

        assertExactlyOneSucceeded(emitirResult, anularResult, "emitir", "anular");

        boolean emitirSucceeded = !(emitirResult instanceof ResponseStatusException);

        // The loser's rejection must use the existing, unmodified business message —
        // proving it validated against the FRESH, already-committed state, not stale data.
        if (emitirSucceeded) {
            assertEquals("Solo se pueden anular facturas en estado pendiente",
                    ((ResponseStatusException) anularResult).getReason());
        } else {
            assertEquals("Solo se pueden emitir facturas en estado pendiente",
                    ((ResponseStatusException) emitirResult).getReason());
        }

        // Final DB state must reflect exactly the winning transition — never a lost update,
        // never both, never neither.
        Factura finalFactura = facturaRepository.findById(facturaId).orElseThrow();
        InvoiceStatus expected = emitirSucceeded ? InvoiceStatus.PAID : InvoiceStatus.CANCELLED;
        assertEquals(expected, finalFactura.getEstado());
    }

    @Test
    @WithMockUser(username = "contador@test.com", roles = {"CONTADOR"})
    void concurrent_procesar_and_rechazar_on_same_devolucion_only_one_succeeds() throws Exception {
        Reservation reservation = newReservation("Cliente Devolucion Concurrencia 1");
        Anticipo anticipo = saveRegisteredAnticipo(reservation);
        Devolucion devolucion = savePendingDevolucion(reservation, anticipo);
        Long devolucionId = devolucion.getId();

        Callable<Object> procesarTask = () -> devolucionService.procesar(devolucionId);
        Callable<Object> rechazarTask = () -> devolucionService.rechazar(devolucionId);

        Object[] results = runConcurrently(procesarTask, rechazarTask);
        Object procesarResult = results[0];
        Object rechazarResult = results[1];

        assertExactlyOneSucceeded(procesarResult, rechazarResult, "procesar", "rechazar");

        boolean procesarSucceeded = !(procesarResult instanceof ResponseStatusException);

        if (procesarSucceeded) {
            assertEquals("Solo se pueden rechazar devoluciones en estado pendiente",
                    ((ResponseStatusException) rechazarResult).getReason());
        } else {
            assertEquals("Solo se pueden procesar devoluciones en estado pendiente",
                    ((ResponseStatusException) procesarResult).getReason());
        }

        Devolucion finalDevolucion = devolucionRepository.findById(devolucionId).orElseThrow();
        DevolucionEstado expected = procesarSucceeded ? DevolucionEstado.PROCESADA : DevolucionEstado.RECHAZADA;
        assertEquals(expected, finalDevolucion.getEstado());
    }

    @Test
    @WithMockUser(username = "contador@test.com", roles = {"CONTADOR"})
    void concurrent_procesar_and_delete_on_same_devolucion_only_one_succeeds() throws Exception {
        Reservation reservation = newReservation("Cliente Devolucion Concurrencia 2");
        Anticipo anticipo = saveRegisteredAnticipo(reservation);
        Devolucion devolucion = savePendingDevolucion(reservation, anticipo);
        Long devolucionId = devolucion.getId();

        Callable<Object> procesarTask = () -> devolucionService.procesar(devolucionId);
        Callable<Object> deleteTask = () -> {
            devolucionService.delete(devolucionId);
            return null; // delete() is void; a clean return means it succeeded.
        };

        Object[] results = runConcurrently(procesarTask, deleteTask);
        Object procesarResult = results[0];
        Object deleteResult = results[1];

        boolean procesarSucceeded = !(procesarResult instanceof ResponseStatusException);
        boolean deleteSucceeded = !(deleteResult instanceof ResponseStatusException);

        assertTrue(procesarSucceeded ^ deleteSucceeded,
                "Expected exactly one of procesar/delete to succeed, but procesar="
                        + procesarSucceeded + " delete=" + deleteSucceeded);

        if (procesarSucceeded) {
            // procesar() won: the row still exists, now PROCESADA, so delete() correctly
            // sees a non-PENDIENTE row and is rejected with 409 (not 404 — the row is
            // still there, just in the wrong state).
            assertInstanceOf(ResponseStatusException.class, deleteResult);
            assertEquals(HttpStatus.CONFLICT, ((ResponseStatusException) deleteResult).getStatusCode());
            assertEquals("Solo se pueden eliminar devoluciones en estado pendiente",
                    ((ResponseStatusException) deleteResult).getReason());

            Devolucion finalDevolucion = devolucionRepository.findById(devolucionId).orElseThrow();
            assertEquals(DevolucionEstado.PROCESADA, finalDevolucion.getEstado());
        } else {
            // delete() won: the row no longer exists at all (a real DELETE, not just a
            // status change), so procesar()'s blocked lock acquisition unblocks to find
            // zero rows and correctly resolves to 404 — never 409, since there is no row
            // left whose estado could be checked. This is the one asymmetry between this
            // test and the emitir/anular and procesar/rechazar cases above, where both
            // competing operations only ever change status and the row always survives.
            assertInstanceOf(ResponseStatusException.class, procesarResult);
            assertEquals(HttpStatus.NOT_FOUND, ((ResponseStatusException) procesarResult).getStatusCode());
            assertEquals("Devolución no encontrada",
                    ((ResponseStatusException) procesarResult).getReason());

            // The winning delete() call means the row no longer exists at all.
            assertTrue(devolucionRepository.findById(devolucionId).isEmpty());
        }
    }
}
