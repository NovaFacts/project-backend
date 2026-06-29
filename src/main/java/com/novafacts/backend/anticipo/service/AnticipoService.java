package com.novafacts.backend.anticipo.service;

import com.novafacts.backend.anticipo.dto.AnticipoRequest;
import com.novafacts.backend.anticipo.dto.AnticipoResponse;
import com.novafacts.backend.anticipo.entity.Anticipo;
import com.novafacts.backend.anticipo.repository.AnticipoRepository;
import com.novafacts.backend.auth.entity.User;
import com.novafacts.backend.auth.repository.UserRepository;
import com.novafacts.backend.reservation.entity.Reservation;
import com.novafacts.backend.reservation.repository.ReservationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AnticipoService {

    private final AnticipoRepository anticipoRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;

    public AnticipoService(AnticipoRepository anticipoRepository,
                           ReservationRepository reservationRepository,
                           UserRepository userRepository) {
        this.anticipoRepository = anticipoRepository;
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<AnticipoResponse> findAll() {
        return anticipoRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AnticipoResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<AnticipoResponse> findByReservaId(Long reservaId) {
        return anticipoRepository.findByReservaId(reservaId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AnticipoResponse create(AnticipoRequest request) {
        Reservation reserva = reservationRepository.findById(request.getReservaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Reserva no encontrada"));

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User usuario = userRepository.findByUsername(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Usuario autenticado no encontrado en el sistema"));

        Anticipo anticipo = new Anticipo();
        anticipo.setReserva(reserva);
        anticipo.setUsuario(usuario);
        anticipo.setMonto(request.getMonto());
        anticipo.setFechaPago(request.getFechaPago());
        anticipo.setMetodoPago(request.getMetodoPago());
        anticipo.setEstado("registrado");

        return toResponse(anticipoRepository.save(anticipo));
    }

    @Transactional
    public void delete(Long id) {
        Anticipo anticipo = getOrThrow(id);
        if ("aplicado".equals(anticipo.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede eliminar un anticipo ya aplicado a una factura");
        }
        anticipoRepository.delete(anticipo);
    }

    private Anticipo getOrThrow(Long id) {
        return anticipoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Anticipo no encontrado"));
    }

    private AnticipoResponse toResponse(Anticipo a) {
        return new AnticipoResponse(
                a.getId(),
                a.getReserva().getId(),
                a.getUsuario().getId(),
                a.getUsuario().getNombre(),
                a.getMonto(),
                a.getFechaPago(),
                a.getMetodoPago(),
                a.getEstado(),
                a.getRegistradoEn()
        );
    }
}
