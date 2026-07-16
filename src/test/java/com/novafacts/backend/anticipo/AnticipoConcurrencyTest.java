package com.novafacts.backend.anticipo;

import com.novafacts.backend.anticipo.entity.Anticipo;
import com.novafacts.backend.anticipo.entity.AnticipoEstado;
import com.novafacts.backend.anticipo.repository.AnticipoRepository;
import com.novafacts.backend.auth.entity.User;
import com.novafacts.backend.auth.repository.UserRepository;
import com.novafacts.backend.canal.entity.Canal;
import com.novafacts.backend.canal.repository.CanalRepository;
import com.novafacts.backend.devolucion.dto.DevolucionRequest;
import com.novafacts.backend.devolucion.repository.DevolucionRepository;
import com.novafacts.backend.devolucion.service.DevolucionService;
import com.novafacts.backend.factura.dto.FacturaRequest;
import com.novafacts.backend.factura.dto.FacturaResponse;
import com.novafacts.backend.factura.repository.FacturaRepository;
import com.novafacts.backend.factura.service.FacturaService;
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
 * C-2: proves AnticipoRepository.findByIdForUpdate()'s pessimistic lock prevents the
 * same anticipo from being simultaneously applied to a factura discount AND refunded
 * via a devolucion. Runs against a real Testcontainers PostgreSQL instance so the
 * SELECT ... FOR UPDATE lock genuinely serializes the two concurrent transactions,
 * rather than relying on simulated/mocked concurrency.
 */
@SpringBootTest
@ActiveProfiles("test")
class AnticipoConcurrencyTest {

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
        canal.setNombre("Canal Concurrencia");
        canal.setTipo("Directo");
        Canal savedCanal = canalRepository.save(canal);

        Temporada temporada = new Temporada();
        temporada.setNombre("Temporada Concurrencia");
        temporada.setFechaInicio(LocalDate.of(2027, 1, 1));
        temporada.setFechaFin(LocalDate.of(2027, 12, 31));
        Temporada savedTemporada = temporadaRepository.save(temporada);

        Property property = new Property();
        property.setName("Finca Concurrencia Test");
        property.setAddress("Vereda Los Pinos");
        Property savedProperty = propertyRepository.save(property);

        PoliticaCancelacion politica = new PoliticaCancelacion();
        politica.setPropiedad(savedProperty);
        politica.setNombre("Política Concurrencia");
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

        Anticipo anticipo = new Anticipo();
        anticipo.setReserva(savedReservation);
        anticipo.setUsuario(savedUser);
        anticipo.setMonto(new BigDecimal("300000.00"));
        anticipo.setFechaPago(LocalDate.of(2027, 4, 15));
        anticipo.setMetodoPago("transferencia");
        anticipo.setEstado(AnticipoEstado.REGISTRADO);
        savedAnticipo = anticipoRepository.save(anticipo);
    }

    @Test
    @WithMockUser(username = "contador@test.com", roles = {"CONTADOR"})
    void concurrent_factura_and_devolucion_never_both_apply_the_same_anticipo() throws Exception {
        // Captured on the test's own thread, where @WithMockUser already populated it —
        // worker threads below don't inherit SecurityContextHolder's ThreadLocal by default.
        SecurityContext sharedContext = SecurityContextHolder.getContext();

        // RF11/RF12/Phase 3: FacturaService now discovers and applies every REGISTRADO
        // anticipo itself and reads subtotal from the reservation's own montoTotal
        // (800000.00, set in setUp()) — there is nothing left on the request to set
        // besides reservaId.
        FacturaRequest facturaRequest = new FacturaRequest();
        facturaRequest.setReservaId(savedReservation.getId());

        DevolucionRequest devolucionRequest = new DevolucionRequest();
        devolucionRequest.setReservaId(savedReservation.getId());
        devolucionRequest.setAnticipoId(savedAnticipo.getId());
        devolucionRequest.setMonto(new BigDecimal("300000.00"));
        devolucionRequest.setMetodo("transferencia");

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<Object> facturaTask = () -> {
            SecurityContextHolder.setContext(sharedContext);
            readyLatch.countDown();
            startLatch.await();
            try {
                return facturaService.create(facturaRequest);
            } catch (ResponseStatusException e) {
                return e;
            }
        };

        Callable<Object> devolucionTask = () -> {
            SecurityContextHolder.setContext(sharedContext);
            readyLatch.countDown();
            startLatch.await();
            try {
                return devolucionService.create(devolucionRequest);
            } catch (ResponseStatusException e) {
                return e;
            }
        };

        Future<Object> facturaFuture = executor.submit(facturaTask);
        Future<Object> devolucionFuture = executor.submit(devolucionTask);

        // Release both threads together once both are parked at startLatch.await(),
        // so they race to lock the same anticipo row as close to simultaneously as possible.
        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        Object facturaResult = facturaFuture.get(10, TimeUnit.SECONDS);
        Object devolucionResult = devolucionFuture.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        boolean facturaSucceeded = !(facturaResult instanceof ResponseStatusException);
        boolean devolucionSucceeded = !(devolucionResult instanceof ResponseStatusException);

        Anticipo finalAnticipo = anticipoRepository.findById(savedAnticipo.getId()).orElseThrow();

        // Unlike a single-anticipoId request, FacturaService.create() no longer asserts
        // "this specific anticipo must still be REGISTRADO" — it collects whatever is
        // currently REGISTRADO and applies it. So factura creation itself does not fail
        // just because devolucion won the race for this one anticipo; it simply excludes
        // it from the discount. What must never happen is the anticipo ending up counted
        // by both operations.
        if (finalAnticipo.getEstado() == AnticipoEstado.APLICADO) {
            // Factura won the lock race first.
            assertTrue(facturaSucceeded, "Factura should succeed when it wins the anticipo lock");
            assertInstanceOf(ResponseStatusException.class, devolucionResult);
            assertEquals(HttpStatus.CONFLICT, ((ResponseStatusException) devolucionResult).getStatusCode());

            FacturaResponse factura = (FacturaResponse) facturaResult;
            assertEquals(0, new BigDecimal("300000.00").compareTo(factura.getDescuentoAnticipo()));
        } else {
            // Devolucion won the lock race first — anticipo is DEVUELTO.
            assertEquals(AnticipoEstado.DEVUELTO, finalAnticipo.getEstado());
            assertTrue(devolucionSucceeded, "Devolucion should succeed when it wins the anticipo lock");
            assertTrue(facturaSucceeded,
                    "Factura creation must still succeed even when the anticipo was refunded first — "
                            + "it should just exclude it from the discount, not fail");

            FacturaResponse factura = (FacturaResponse) facturaResult;
            assertEquals(0, BigDecimal.ZERO.compareTo(factura.getDescuentoAnticipo()));
        }

        // Whichever way the race went, the anticipo was mutated by exactly one operation.
        assertEquals(1, facturaRepository.count());
        assertEquals(devolucionSucceeded ? 1 : 0, devolucionRepository.count());
    }

    @Test
    @WithMockUser(username = "contador@test.com", roles = {"CONTADOR"})
    void concurrent_factura_creations_for_same_reservation_apply_anticipo_exactly_once() throws Exception {
        // RF11/RF12 concurrency requirement: two simultaneous invoice-creation attempts
        // for the same reservation must never both apply savedAnticipo, and whichever
        // attempt ultimately fails (rejected either by the existsByReservaId check or by
        // the DB's unique constraint on factura.reserva_id) must leave no trace — no
        // partial anticipo mutation survives a losing transaction.
        SecurityContext sharedContext = SecurityContextHolder.getContext();

        FacturaRequest requestA = new FacturaRequest();
        requestA.setReservaId(savedReservation.getId());

        FacturaRequest requestB = new FacturaRequest();
        requestB.setReservaId(savedReservation.getId());

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<Object> taskA = () -> {
            SecurityContextHolder.setContext(sharedContext);
            readyLatch.countDown();
            startLatch.await();
            try {
                return facturaService.create(requestA);
            } catch (RuntimeException e) {
                return e;
            }
        };
        Callable<Object> taskB = () -> {
            SecurityContextHolder.setContext(sharedContext);
            readyLatch.countDown();
            startLatch.await();
            try {
                return facturaService.create(requestB);
            } catch (RuntimeException e) {
                return e;
            }
        };

        Future<Object> futureA = executor.submit(taskA);
        Future<Object> futureB = executor.submit(taskB);

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        Object resultA = futureA.get(10, TimeUnit.SECONDS);
        Object resultB = futureB.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        boolean succeededA = resultA instanceof FacturaResponse;
        boolean succeededB = resultB instanceof FacturaResponse;

        // The DB's unique constraint on factura.reserva_id guarantees exactly one wins,
        // regardless of how the two transactions interleave around the anticipo lock.
        assertTrue(succeededA ^ succeededB,
                "Expected exactly one concurrent factura creation to succeed, but A=" + succeededA + " B=" + succeededB);

        assertEquals(1, facturaRepository.count());

        FacturaResponse winner = (FacturaResponse) (succeededA ? resultA : resultB);
        assertEquals(0, new BigDecimal("300000.00").compareTo(winner.getDescuentoAnticipo()));

        // The anticipo was consumed by exactly the winning transaction — the loser's
        // attempt to apply it (if it got there first) was rolled back along with its
        // failed Factura insert, never left half-applied.
        Anticipo finalAnticipo = anticipoRepository.findById(savedAnticipo.getId()).orElseThrow();
        assertEquals(AnticipoEstado.APLICADO, finalAnticipo.getEstado());
    }
}
