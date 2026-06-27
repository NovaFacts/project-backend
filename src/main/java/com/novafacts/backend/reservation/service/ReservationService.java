package com.novafacts.backend.reservation.service;

import com.novafacts.backend.guest.repository.GuestRepository;
import com.novafacts.backend.property.entity.Property;
import com.novafacts.backend.property.repository.PropertyRepository;
import com.novafacts.backend.reservation.dto.CreateReservationRequest;
import com.novafacts.backend.reservation.dto.ReservationResponse;
import com.novafacts.backend.reservation.dto.UpdateReservationRequest;
import com.novafacts.backend.reservation.entity.Reservation;
import com.novafacts.backend.reservation.entity.ReservationStatus;
import com.novafacts.backend.reservation.repository.ReservationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final GuestRepository guestRepository;
    private final PropertyRepository propertyRepository;

    public ReservationService(ReservationRepository reservationRepository,
                              GuestRepository guestRepository,
                              PropertyRepository propertyRepository) {
        this.reservationRepository = reservationRepository;
        this.guestRepository = guestRepository;
        this.propertyRepository = propertyRepository;
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> findAll() {
        return reservationRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReservationResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    @Transactional
    public ReservationResponse create(CreateReservationRequest request) {
        validateGuestExists(request.getGuestId());
        Property property = getPropertyOrThrow(request.getPropertyId());
        validateDates(request.getCheckIn(), request.getCheckOut());
        validateGuestCount(request.getGuestCount(), property.getCapacity());
        // checkOut is passed as the CheckInBefore bound; checkIn as the CheckOutAfter bound — see repository.
        if (reservationRepository.existsByPropertyIdAndCheckInBeforeAndCheckOutAfterAndStatus(
                request.getPropertyId(), request.getCheckOut(), request.getCheckIn(),
                ReservationStatus.CONFIRMED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La propiedad ya tiene una reserva en esas fechas");
        }
        Reservation reservation = new Reservation();
        reservation.setGuestId(request.getGuestId());
        reservation.setPropertyId(request.getPropertyId());
        reservation.setCheckIn(request.getCheckIn());
        reservation.setCheckOut(request.getCheckOut());
        reservation.setGuestCount(request.getGuestCount());
        reservation.setStatus(ReservationStatus.CONFIRMED);
        return toResponse(reservationRepository.save(reservation));
    }

    @Transactional
    public ReservationResponse update(Long id, UpdateReservationRequest request) {
        Reservation reservation = getOrThrow(id);
        validateGuestExists(request.getGuestId());
        Property property = getPropertyOrThrow(request.getPropertyId());
        validateDates(request.getCheckIn(), request.getCheckOut());
        validateGuestCount(request.getGuestCount(), property.getCapacity());
        // checkOut is passed as the CheckInBefore bound; checkIn as the CheckOutAfter bound — see repository.
        if (reservationRepository.existsByPropertyIdAndCheckInBeforeAndCheckOutAfterAndStatusAndIdNot(
                request.getPropertyId(), request.getCheckOut(), request.getCheckIn(),
                ReservationStatus.CONFIRMED, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La propiedad ya tiene una reserva en esas fechas");
        }
        reservation.setGuestId(request.getGuestId());
        reservation.setPropertyId(request.getPropertyId());
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

    private void validateGuestExists(Long guestId) {
        if (!guestRepository.existsById(guestId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Huésped no encontrado");
        }
    }

    private Property getPropertyOrThrow(Long propertyId) {
        return propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Propiedad no encontrada"));
    }

    private void validateDates(LocalDate checkIn, LocalDate checkOut) {
        if (!checkIn.isBefore(checkOut)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de inicio debe ser anterior a la fecha de fin");
        }
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights > 30) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La reserva no puede superar 30 noches");
        }
    }

    private void validateGuestCount(Integer guestCount, Integer propertyCapacity) {
        if (guestCount > propertyCapacity) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cantidad de huéspedes supera la capacidad de la propiedad");
        }
    }

    private Reservation getOrThrow(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Reserva no encontrada"));
    }

    private ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getGuestId(),
                reservation.getPropertyId(),
                reservation.getCheckIn(),
                reservation.getCheckOut(),
                reservation.getGuestCount(),
                reservation.getStatus(),
                reservation.getCreatedAt()
        );
    }
}
