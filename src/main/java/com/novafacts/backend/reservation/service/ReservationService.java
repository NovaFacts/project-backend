package com.novafacts.backend.reservation.service;

import com.novafacts.backend.auth.entity.User;
import com.novafacts.backend.auth.repository.UserRepository;
import com.novafacts.backend.canal.entity.Canal;
import com.novafacts.backend.canal.repository.CanalRepository;
import com.novafacts.backend.politicacancelacion.entity.PoliticaCancelacion;
import com.novafacts.backend.politicacancelacion.repository.PoliticaCancelacionRepository;
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

    private final ReservationRepository reservationRepository;
    private final CanalRepository canalRepository;
    private final TemporadaRepository temporadaRepository;
    private final PoliticaCancelacionRepository politicaRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;

    public ReservationService(ReservationRepository reservationRepository,
                              CanalRepository canalRepository,
                              TemporadaRepository temporadaRepository,
                              PoliticaCancelacionRepository politicaRepository,
                              PropertyRepository propertyRepository,
                              UserRepository userRepository) {
        this.reservationRepository = reservationRepository;
        this.canalRepository = canalRepository;
        this.temporadaRepository = temporadaRepository;
        this.politicaRepository = politicaRepository;
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
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
        Canal canal = getCanalOrThrow(request.getCanalId());
        Temporada temporada = getTemporadaOrThrow(request.getTemporadaId());
        PoliticaCancelacion politica = getPoliticaOrThrow(request.getPoliticaCancelacionId());
        validatePropertyExists(request.getPropertyId());
        validatePoliticaMatchesProperty(politica, request.getPropertyId());
        validateDates(request.getCheckIn(), request.getCheckOut());

        if (reservationRepository.existsByPropertyIdAndCheckInBeforeAndCheckOutAfterAndStatus(
                request.getPropertyId(), request.getCheckOut(), request.getCheckIn(),
                ReservationStatus.CONFIRMED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La propiedad ya tiene una reserva confirmada en esas fechas");
        }

        // Extract the authenticated user securely from the SecurityContext.
        // The JWT filter has already validated the token; this value was never supplied
        // by the client in the request body.
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
        Canal canal = getCanalOrThrow(request.getCanalId());
        Temporada temporada = getTemporadaOrThrow(request.getTemporadaId());
        PoliticaCancelacion politica = getPoliticaOrThrow(request.getPoliticaCancelacionId());
        validatePropertyExists(request.getPropertyId());
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
        reservationRepository.delete(getOrThrow(id));
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

    private void validatePropertyExists(Long propertyId) {
        if (!propertyRepository.existsById(propertyId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Propiedad no encontrada");
        }
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
