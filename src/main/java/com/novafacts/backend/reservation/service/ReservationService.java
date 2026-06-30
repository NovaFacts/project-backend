package com.novafacts.backend.reservation.service;

import com.novafacts.backend.anticipo.repository.AnticipoRepository;
import com.novafacts.backend.auth.entity.User;
import com.novafacts.backend.auth.repository.UserRepository;
import com.novafacts.backend.canal.entity.Canal;
import com.novafacts.backend.canal.repository.CanalRepository;
import com.novafacts.backend.devolucion.repository.DevolucionRepository;
import com.novafacts.backend.factura.repository.FacturaRepository;
import com.novafacts.backend.penalidad.repository.PenalidadRepository;
import com.novafacts.backend.politicacancelacion.entity.PoliticaCancelacion;
import com.novafacts.backend.politicacancelacion.repository.PoliticaCancelacionRepository;
import com.novafacts.backend.property.entity.Property;
import com.novafacts.backend.property.repository.PropertyRepository;
import com.novafacts.backend.reservation.dto.CreateReservationRequest;
import com.novafacts.backend.reservation.dto.ReservationResponse;
import com.novafacts.backend.reservation.dto.UpdateReservationRequest;
import com.novafacts.backend.reservation.entity.Reservation;
import com.novafacts.backend.reservation.entity.ReservationStatus;
import com.novafacts.backend.reservation.repository.ReservationRepository;
import com.novafacts.backend.temporada.entity.Temporada;
import com.novafacts.backend.temporada.repository.TemporadaRepository;
import com.novafacts.backend.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class ReservationService {

    private final ReservationRepository           reservationRepository;
    private final CanalRepository                 canalRepository;
    private final TemporadaRepository             temporadaRepository;
    private final PoliticaCancelacionRepository   politicaRepository;
    private final PropertyRepository              propertyRepository;
    private final UserRepository                  userRepository;
    // financial-child repositories — used only in delete() guard (read-only)
    private final AnticipoRepository              anticipoRepository;
    private final PenalidadRepository             penalidadRepository;
    private final FacturaRepository               facturaRepository;
    private final DevolucionRepository            devolucionRepository;

    public ReservationService(ReservationRepository reservationRepository,
                              CanalRepository canalRepository,
                              TemporadaRepository temporadaRepository,
                              PoliticaCancelacionRepository politicaRepository,
                              PropertyRepository propertyRepository,
                              UserRepository userRepository,
                              AnticipoRepository anticipoRepository,
                              PenalidadRepository penalidadRepository,
                              FacturaRepository facturaRepository,
                              DevolucionRepository devolucionRepository) {
        this.reservationRepository = reservationRepository;
        this.canalRepository       = canalRepository;
        this.temporadaRepository   = temporadaRepository;
        this.politicaRepository    = politicaRepository;
        this.propertyRepository    = propertyRepository;
        this.userRepository        = userRepository;
        this.anticipoRepository    = anticipoRepository;
        this.penalidadRepository   = penalidadRepository;
        this.facturaRepository     = facturaRepository;
        this.devolucionRepository  = devolucionRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> findAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("checkIn").descending());
        return new PageResponse<>(reservationRepository.findAll(pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public ReservationResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    @Transactional
    public ReservationResponse create(CreateReservationRequest request) {
        // MEDIUM-13: reject reservations with a check-in date already in the past
        if (request.getCheckIn().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de check-in no puede estar en el pasado.");
        }

        Canal canal           = getCanalOrThrow(request.getCanalId());
        Temporada temporada   = getTemporadaOrThrow(request.getTemporadaId());
        PoliticaCancelacion politica = getPoliticaOrThrow(request.getPoliticaCancelacionId());

        // CRITICAL-4: acquire a row-level exclusive lock on the propiedad row before
        // reading the reserva table for overlap. This serializes all concurrent
        // reservation attempts for the same property so that the check-then-insert
        // is atomic at the DB level. The lock is released on transaction commit.
        lockPropertyOrThrow(request.getPropertyId());

        validatePoliticaMatchesProperty(politica, request.getPropertyId());
        validateDates(request.getCheckIn(), request.getCheckOut());

        if (reservationRepository.existsByPropertyIdAndCheckInBeforeAndCheckOutAfterAndStatus(
                request.getPropertyId(), request.getCheckOut(), request.getCheckIn(),
                ReservationStatus.CONFIRMED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La propiedad ya tiene una reserva confirmada en esas fechas");
        }

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User usuarioCreador = userRepository.findByUsername(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Usuario autenticado no encontrado en el sistema"));

        Reservation reservation = new Reservation();
        reservation.setCanal(canal);
        reservation.setTemporada(temporada);
        reservation.setPoliticaCancelacion(politica);
        reservation.setUsuarioCreador(usuarioCreador);
        reservation.setPropertyId(request.getPropertyId());
        reservation.setClienteNombre(request.getClienteNombre());
        reservation.setClienteEmail(request.getClienteEmail());
        reservation.setClienteTelefono(request.getClienteTelefono());
        reservation.setMontoTotal(request.getMontoTotal());
        reservation.setCheckIn(request.getCheckIn());
        reservation.setCheckOut(request.getCheckOut());
        reservation.setGuestCount(request.getGuestCount());
        reservation.setStatus(ReservationStatus.CONFIRMED);

        return toResponse(reservationRepository.save(reservation));
    }

    @Transactional
    public ReservationResponse update(Long id, UpdateReservationRequest request) {
        Reservation reservation = getOrThrow(id);

        // Validate the status transition before acquiring the pessimistic lock
        // so we fail fast without holding a DB row lock for an invalid request.
        validateStatusTransition(reservation.getStatus(), request.getStatus());

        Canal canal             = getCanalOrThrow(request.getCanalId());
        Temporada temporada     = getTemporadaOrThrow(request.getTemporadaId());
        PoliticaCancelacion politica = getPoliticaOrThrow(request.getPoliticaCancelacionId());

        // CRITICAL-4: lock the target property (which may differ from the current one
        // if the property is being changed) before the overlap check.
        lockPropertyOrThrow(request.getPropertyId());

        validatePoliticaMatchesProperty(politica, request.getPropertyId());
        validateDates(request.getCheckIn(), request.getCheckOut());

        if (reservationRepository.existsByPropertyIdAndCheckInBeforeAndCheckOutAfterAndStatusAndIdNot(
                request.getPropertyId(), request.getCheckOut(), request.getCheckIn(),
                ReservationStatus.CONFIRMED, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La propiedad ya tiene una reserva confirmada en esas fechas");
        }

        reservation.setCanal(canal);
        reservation.setTemporada(temporada);
        reservation.setPoliticaCancelacion(politica);
        reservation.setPropertyId(request.getPropertyId());
        reservation.setClienteNombre(request.getClienteNombre());
        reservation.setClienteEmail(request.getClienteEmail());
        reservation.setClienteTelefono(request.getClienteTelefono());
        reservation.setMontoTotal(request.getMontoTotal());
        reservation.setCheckIn(request.getCheckIn());
        reservation.setCheckOut(request.getCheckOut());
        reservation.setGuestCount(request.getGuestCount());
        reservation.setStatus(request.getStatus());

        return toResponse(reservationRepository.save(reservation));
    }

    @Transactional
    public void delete(Long id) {
        Reservation reservation = getOrThrow(id);

        // HIGH-9: check for FK children before attempting the delete so that the
        // caller receives a descriptive error instead of a generic "Conflicto de datos"
        // from the DataIntegrityViolationException handler.
        // Short-circuit evaluation stops at the first child found.
        boolean hasFinancialHistory =
                anticipoRepository.existsByReservaId(id)   ||
                penalidadRepository.existsByReservaId(id)  ||
                facturaRepository.existsByReservaId(id)    ||
                devolucionRepository.existsByReservaId(id);

        if (hasFinancialHistory) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede eliminar la reserva porque tiene historial financiero asociado.");
        }

        reservationRepository.delete(reservation);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * CRITICAL-4: Fetches the propiedad row under a pessimistic write lock
     * (SELECT ... FOR UPDATE).  Combines the existence check, active check, and lock
     * acquisition. Must be called within an active @Transactional scope.
     *
     * The active check is performed after acquiring the lock so that both the
     * activa read and the subsequent reservation insert are serialized at the DB level.
     * A concurrent soft-delete (UPDATE propiedad SET activa=false) will block on
     * this same row lock until the surrounding transaction commits or rolls back.
     */
    private void lockPropertyOrThrow(Long propertyId) {
        Property property = propertyRepository.findByIdForUpdate(propertyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Propiedad no encontrada"));
        if (!Boolean.TRUE.equals(property.getActiva())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La propiedad no está activa y no puede recibir nuevas reservas");
        }
    }

    private Canal getCanalOrThrow(Integer id) {
        return canalRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Canal no encontrado"));
    }

    private Temporada getTemporadaOrThrow(Integer id) {
        return temporadaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Temporada no encontrada"));
    }

    private PoliticaCancelacion getPoliticaOrThrow(Integer id) {
        return politicaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Política de cancelación no encontrada"));
    }

    private void validatePoliticaMatchesProperty(PoliticaCancelacion politica, Long propertyId) {
        if (!politica.getPropiedad().getId().equals(propertyId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La política de cancelación no corresponde a la propiedad seleccionada");
        }
    }

    private void validateDates(LocalDate checkIn, LocalDate checkOut) {
        if (!checkIn.isBefore(checkOut)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de inicio debe ser anterior a la fecha de fin");
        }
        if (ChronoUnit.DAYS.between(checkIn, checkOut) > 30) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La reserva no puede superar 30 noches");
        }
    }

    /**
     * Enforces the reservation status state machine.
     * CONFIRMED → COMPLETED and CONFIRMED → CANCELLED are the only forward transitions.
     * COMPLETED and CANCELLED are terminal: no further status changes are permitted.
     * Same-status updates (no-op) are always allowed so that callers can update
     * other reservation fields (dates, canal, guests) without being forced to
     * provide a new status.
     */
    private void validateStatusTransition(ReservationStatus current, ReservationStatus next) {
        if (current == next) return;
        if (current == ReservationStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Una reserva completada no puede cambiar de estado");
        }
        if (current == ReservationStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Una reserva cancelada no puede cambiar de estado");
        }
        // current == CONFIRMED: CONFIRMED → COMPLETED and CONFIRMED → CANCELLED are both valid.
    }

    private Reservation getOrThrow(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Reserva no encontrada"));
    }

    private ReservationResponse toResponse(Reservation r) {
        return new ReservationResponse(
                r.getId(),
                r.getPropertyId(),
                r.getCanal().getId(),
                r.getCanal().getNombre(),
                r.getTemporada().getId(),
                r.getTemporada().getNombre(),
                r.getPoliticaCancelacion().getId(),
                r.getPoliticaCancelacion().getNombre(),
                r.getUsuarioCreador().getId(),
                r.getUsuarioCreador().getNombre(),
                r.getClienteNombre(),
                r.getClienteEmail(),
                r.getClienteTelefono(),
                r.getMontoTotal(),
                r.getCheckIn(),
                r.getCheckOut(),
                r.getGuestCount(),
                r.getStatus(),
                r.getCreatedAt()
        );
    }
}
