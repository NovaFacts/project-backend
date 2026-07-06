package com.novafacts.backend.reservation;

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
import com.novafacts.backend.reservation.dto.UpdateReservationRequest;
import com.novafacts.backend.reservation.entity.Reservation;
import com.novafacts.backend.reservation.entity.ReservationStatus;
import com.novafacts.backend.reservation.repository.ReservationRepository;
import com.novafacts.backend.reservation.service.ReservationService;
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
 * H-3 (AUDIT_v5): proves ReservationRepository.findByIdForUpdate()'s pessimistic lock
 * prevents two concurrent conflicting status transitions on the same reservation from
 * both succeeding. Before this fix, two concurrent PUT /api/reservas/{id} requests —
 * one CONFIRMED->CANCELLED, one CONFIRMED->COMPLETED — both returned 200 with
 * mutually-exclusive responses, and the database silently reflected only whichever
 * transaction committed last (reproduced live during investigation, 4/4 trials).
 *
 * Runs against a real Testcontainers PostgreSQL instance so the SELECT ... FOR UPDATE
 * lock genuinely serializes the two concurrent transactions, mirroring
 * AnticipoConcurrencyTest's structure exactly.
 *
 * Known, accepted, out-of-scope limitation (not covered by this test, documented in
 * the investigation report): two concurrent updates changing different, non-validated
 * fields (e.g. clienteNombre vs. guestCount) can still silently overwrite each other,
 * since update() is a full-PUT endpoint with no per-field patch semantics or
 * ETag/@Version-based optimistic concurrency at the API level. The pessimistic lock
 * only prevents a race from causing *validation* (like the status state machine) to
 * pass on stale data — it cannot make one full-object PUT aware of another's
 * non-conflicting field changes without a materially different mechanism.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReservationConcurrencyTest {

    @Autowired private ReservationService reservationService;
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
    private Integer canalId;
    private Integer temporadaId;
    private Integer politicaId;
    private Long propertyId;

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
        rol.setNombre("Recepcionista");
        Rol savedRol = rolRepository.save(rol);

        User user = new User();
        user.setUsername("recepcion@test.com");
        user.setPassword("$2a$10$irrelevant");
        user.setNombre("Recepcionista Test");
        user.setRol(savedRol);
        User savedUser = userRepository.save(user);

        Canal canal = new Canal();
        canal.setNombre("Canal Concurrencia Reserva");
        canal.setTipo("Directo");
        Canal savedCanal = canalRepository.save(canal);
        canalId = savedCanal.getId();

        Temporada temporada = new Temporada();
        temporada.setNombre("Temporada Concurrencia Reserva");
        temporada.setFechaInicio(LocalDate.of(2027, 1, 1));
        temporada.setFechaFin(LocalDate.of(2027, 12, 31));
        Temporada savedTemporada = temporadaRepository.save(temporada);
        temporadaId = savedTemporada.getId();

        Property property = new Property();
        property.setName("Finca Concurrencia Reserva Test");
        property.setAddress("Vereda El Rosal");
        Property savedProperty = propertyRepository.save(property);
        propertyId = savedProperty.getId();

        PoliticaCancelacion politica = new PoliticaCancelacion();
        politica.setPropiedad(savedProperty);
        politica.setNombre("Política Concurrencia Reserva");
        politica.setPorcentajeReembolso(new BigDecimal("50.00"));
        politica.setDiasAviso(3);
        PoliticaCancelacion savedPolitica = politicaRepository.save(politica);
        politicaId = savedPolitica.getId();

        Reservation reservation = new Reservation();
        reservation.setCanal(savedCanal);
        reservation.setTemporada(savedTemporada);
        reservation.setPoliticaCancelacion(savedPolitica);
        reservation.setUsuarioCreador(savedUser);
        reservation.setPropertyId(savedProperty.getId());
        reservation.setClienteNombre("Diana Restrepo");
        reservation.setMontoTotal(new BigDecimal("900000.00"));
        reservation.setCheckIn(LocalDate.of(2027, 6, 1));
        reservation.setCheckOut(LocalDate.of(2027, 6, 5));
        reservation.setGuestCount(2);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        savedReservation = reservationRepository.save(reservation);
    }

    private UpdateReservationRequest requestWithStatus(ReservationStatus status) {
        UpdateReservationRequest request = new UpdateReservationRequest();
        request.setCanalId(canalId);
        request.setTemporadaId(temporadaId);
        request.setPoliticaCancelacionId(politicaId);
        request.setPropertyId(propertyId);
        request.setClienteNombre("Diana Restrepo");
        request.setMontoTotal(new BigDecimal("900000.00"));
        request.setCheckIn(LocalDate.of(2027, 6, 1));
        request.setCheckOut(LocalDate.of(2027, 6, 5));
        request.setGuestCount(2);
        request.setStatus(status);
        return request;
    }

    @Test
    @WithMockUser(username = "recepcion@test.com", roles = {"RECEPCIONISTA"})
    void concurrent_cancel_and_complete_on_same_reservation_only_one_succeeds() throws Exception {
        SecurityContext sharedContext = SecurityContextHolder.getContext();
        Long reservationId = savedReservation.getId();

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<Object> cancelTask = () -> {
            SecurityContextHolder.setContext(sharedContext);
            readyLatch.countDown();
            startLatch.await();
            try {
                return reservationService.update(reservationId, requestWithStatus(ReservationStatus.CANCELLED));
            } catch (ResponseStatusException e) {
                return e;
            }
        };

        Callable<Object> completeTask = () -> {
            SecurityContextHolder.setContext(sharedContext);
            readyLatch.countDown();
            startLatch.await();
            try {
                return reservationService.update(reservationId, requestWithStatus(ReservationStatus.COMPLETED));
            } catch (ResponseStatusException e) {
                return e;
            }
        };

        Future<Object> cancelFuture = executor.submit(cancelTask);
        Future<Object> completeFuture = executor.submit(completeTask);

        // Release both threads together once both are parked at startLatch.await(),
        // so they race to lock the same reservation row as close to simultaneously as possible.
        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        Object cancelResult = cancelFuture.get(10, TimeUnit.SECONDS);
        Object completeResult = completeFuture.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        boolean cancelSucceeded = !(cancelResult instanceof ResponseStatusException);
        boolean completeSucceeded = !(completeResult instanceof ResponseStatusException);

        // Exactly one of the two conflicting transitions must win — never both (the H-3
        // bug, reproduced live before this fix), never neither (both are valid transitions
        // from CONFIRMED in isolation).
        assertTrue(cancelSucceeded ^ completeSucceeded,
                "Expected exactly one of cancel/complete to succeed, but cancel="
                        + cancelSucceeded + " complete=" + completeSucceeded);

        // The loser must be rejected with the pre-existing, unmodified terminal-state
        // message — proving the second transaction validated against the FRESH,
        // already-committed state (not the stale state it originally read).
        if (!cancelSucceeded) {
            assertInstanceOf(ResponseStatusException.class, cancelResult);
            assertEquals(HttpStatus.CONFLICT, ((ResponseStatusException) cancelResult).getStatusCode());
        }
        if (!completeSucceeded) {
            assertInstanceOf(ResponseStatusException.class, completeResult);
            assertEquals(HttpStatus.CONFLICT, ((ResponseStatusException) completeResult).getStatusCode());
        }

        // Final DB state must reflect exactly the winning transition, never a lost update.
        Reservation finalReservation = reservationRepository.findById(reservationId).orElseThrow();
        ReservationStatus expectedStatus = cancelSucceeded ? ReservationStatus.CANCELLED : ReservationStatus.COMPLETED;
        assertEquals(expectedStatus, finalReservation.getStatus());
    }
}
