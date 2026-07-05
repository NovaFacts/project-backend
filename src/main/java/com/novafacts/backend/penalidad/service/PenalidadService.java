package com.novafacts.backend.penalidad.service;

import com.novafacts.backend.auth.entity.User;
import com.novafacts.backend.auth.repository.UserRepository;
import com.novafacts.backend.penalidad.dto.PenalidadRequest;
import com.novafacts.backend.penalidad.dto.PenalidadResponse;
import com.novafacts.backend.penalidad.entity.Penalidad;
import com.novafacts.backend.penalidad.repository.PenalidadRepository;
import com.novafacts.backend.politicacancelacion.entity.PoliticaCancelacion;
import com.novafacts.backend.reservation.entity.Reservation;
import com.novafacts.backend.reservation.entity.ReservationStatus;
import com.novafacts.backend.reservation.repository.ReservationRepository;
import com.novafacts.backend.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class PenalidadService {

    private final PenalidadRepository penalidadRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;

    public PenalidadService(PenalidadRepository penalidadRepository,
                            ReservationRepository reservationRepository,
                            UserRepository userRepository) {
        this.penalidadRepository = penalidadRepository;
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<PenalidadResponse> findAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("calculadoEn").descending());
        return new PageResponse<>(penalidadRepository.findAllWithUsuario(pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PenalidadResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<PenalidadResponse> findByReservaId(Long reservaId) {
        return penalidadRepository.findByReservaId(reservaId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PenalidadResponse create(PenalidadRequest request) {
        Reservation reserva = reservationRepository.findById(request.getReservaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Reserva no encontrada"));

        if (reserva.getStatus() != ReservationStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo se puede crear una penalidad sobre una reserva cancelada");
        }

        // MEDIUM-12: montoAprobado must not exceed the maximum allowed by the cancellation policy.
        // Maximum penalty = montoTotal × (1 − porcentajeReembolso / 100)
        // Example: 50% refund policy on 1,000,000 COP → max penalty = 500,000 COP
        PoliticaCancelacion politica = reserva.getPoliticaCancelacion();
        BigDecimal maximoPenalidad = reserva.getMontoTotal()
                .multiply(
                    BigDecimal.ONE.subtract(
                        politica.getPorcentajeReembolso()
                                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                    )
                )
                .setScale(2, RoundingMode.HALF_UP);

        if (request.getMontoAprobado().compareTo(maximoPenalidad) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format(
                        "El monto aprobado (%.2f) supera el máximo permitido por la política " +
                        "de cancelación '%.0f%% de reembolso' (máximo penalidad: %.2f).",
                        request.getMontoAprobado(),
                        politica.getPorcentajeReembolso(),
                        maximoPenalidad));
        }

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User usuario = userRepository.findByUsername(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Usuario autenticado no encontrado en el sistema"));

        BigDecimal montoCondonado = request.getMontoCondonado() != null
                ? request.getMontoCondonado()
                : BigDecimal.ZERO;

        Penalidad penalidad = new Penalidad();
        penalidad.setReserva(reserva);
        penalidad.setUsuario(usuario);
        penalidad.setMontoSegunPolitica(request.getMontoSegunPolitica());
        penalidad.setMontoAprobado(request.getMontoAprobado());
        penalidad.setMontoCondonado(montoCondonado);
        penalidad.setFechaCancelacion(request.getFechaCancelacion());
        penalidad.setMotivo(request.getMotivo());

        return toResponse(penalidadRepository.save(penalidad));
    }

    @Transactional
    public void delete(Long id) {
        penalidadRepository.delete(getOrThrow(id));
    }

    private Penalidad getOrThrow(Long id) {
        return penalidadRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Penalidad no encontrada"));
    }

    private PenalidadResponse toResponse(Penalidad p) {
        return new PenalidadResponse(
                p.getId(),
                p.getReserva().getId(),
                p.getUsuario().getId(),
                p.getUsuario().getNombre(),
                p.getMontoSegunPolitica(),
                p.getMontoAprobado(),
                p.getMontoCondonado(),
                p.getFechaCancelacion(),
                p.getMotivo(),
                p.getCalculadoEn()
        );
    }
}
