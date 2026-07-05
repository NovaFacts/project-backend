package com.novafacts.backend.devolucion.service;

import com.novafacts.backend.anticipo.entity.Anticipo;
import com.novafacts.backend.anticipo.entity.AnticipoEstado;
import com.novafacts.backend.anticipo.repository.AnticipoRepository;
import com.novafacts.backend.auth.entity.User;
import com.novafacts.backend.auth.repository.UserRepository;
import com.novafacts.backend.devolucion.dto.DevolucionRequest;
import com.novafacts.backend.devolucion.dto.DevolucionResponse;
import com.novafacts.backend.devolucion.entity.Devolucion;
import com.novafacts.backend.devolucion.entity.DevolucionEstado;
import com.novafacts.backend.devolucion.repository.DevolucionRepository;
import com.novafacts.backend.reservation.entity.Reservation;
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
import java.time.LocalDateTime;
import java.util.List;

@Service
public class DevolucionService {

    private final DevolucionRepository devolucionRepository;
    private final ReservationRepository reservationRepository;
    private final AnticipoRepository anticipoRepository;
    private final UserRepository userRepository;

    public DevolucionService(DevolucionRepository devolucionRepository,
                             ReservationRepository reservationRepository,
                             AnticipoRepository anticipoRepository,
                             UserRepository userRepository) {
        this.devolucionRepository = devolucionRepository;
        this.reservationRepository = reservationRepository;
        this.anticipoRepository   = anticipoRepository;
        this.userRepository       = userRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<DevolucionResponse> findAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("generadaEn").descending());
        return new PageResponse<>(devolucionRepository.findAllWithUsuario(pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public DevolucionResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<DevolucionResponse> findByReservaId(Long reservaId) {
        return devolucionRepository.findByReservaId(reservaId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public DevolucionResponse create(DevolucionRequest request) {
        Reservation reserva = reservationRepository.findById(request.getReservaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Reserva no encontrada"));

        // C-2: lock the anticipo row before checking its estado, so a concurrent
        // FacturaService.applyAnticipo() call on the same anticipoId cannot also pass
        // the REGISTRADO check and apply what this transaction is about to refund.
        Anticipo anticipo = anticipoRepository.findByIdForUpdate(request.getAnticipoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Anticipo no encontrado"));

        if (!anticipo.getReserva().getId().equals(reserva.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El anticipo indicado no pertenece a la reserva");
        }

        // CRITICAL-3: guard against double-refund.
        // APLICADO means the anticipo was already deducted from a factura.
        // DEVUELTO means a prior devolucion already exists for this anticipo.
        if (anticipo.getEstado() != AnticipoEstado.REGISTRADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El anticipo ya fue aplicado a una factura o devuelto y no puede ser reembolsado");
        }

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User usuario = userRepository.findByUsername(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Usuario autenticado no encontrado en el sistema"));

        BigDecimal monto = request.getMonto().setScale(2, RoundingMode.HALF_UP);

        Devolucion devolucion = new Devolucion();
        devolucion.setReserva(reserva);
        devolucion.setAnticipo(anticipo);
        devolucion.setUsuario(usuario);
        devolucion.setMonto(monto);
        devolucion.setMetodo(request.getMetodo());
        devolucion.setEstado(DevolucionEstado.PENDIENTE);

        anticipo.setEstado(AnticipoEstado.DEVUELTO);
        anticipoRepository.save(anticipo);

        return toResponse(devolucionRepository.save(devolucion));
    }

    @Transactional
    public DevolucionResponse procesar(Long id) {
        Devolucion devolucion = getOrThrow(id);
        if (devolucion.getEstado() != DevolucionEstado.PENDIENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo se pueden procesar devoluciones en estado pendiente");
        }
        devolucion.setEstado(DevolucionEstado.PROCESADA);
        devolucion.setProcesadaEn(LocalDateTime.now());
        return toResponse(devolucionRepository.save(devolucion));
    }

    @Transactional
    public DevolucionResponse rechazar(Long id) {
        Devolucion devolucion = getOrThrow(id);
        if (devolucion.getEstado() != DevolucionEstado.PENDIENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo se pueden rechazar devoluciones en estado pendiente");
        }
        Anticipo anticipo = devolucion.getAnticipo();
        anticipo.setEstado(AnticipoEstado.REGISTRADO);
        anticipoRepository.save(anticipo);
        devolucion.setEstado(DevolucionEstado.RECHAZADA);
        return toResponse(devolucionRepository.save(devolucion));
    }

    @Transactional
    public void delete(Long id) {
        Devolucion devolucion = getOrThrow(id);
        if (devolucion.getEstado() != DevolucionEstado.PENDIENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo se pueden eliminar devoluciones en estado pendiente");
        }
        Anticipo anticipo = devolucion.getAnticipo();
        anticipo.setEstado(AnticipoEstado.REGISTRADO);
        anticipoRepository.save(anticipo);
        devolucionRepository.delete(devolucion);
    }

    private Devolucion getOrThrow(Long id) {
        return devolucionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Devolución no encontrada"));
    }

    private DevolucionResponse toResponse(Devolucion d) {
        return new DevolucionResponse(
                d.getId(),
                d.getReserva().getId(),
                d.getAnticipo().getId(),
                d.getUsuario().getId(),
                d.getUsuario().getNombre(),
                d.getMonto(),
                d.getMetodo(),
                d.getEstado().name().toLowerCase(),
                d.getGeneradaEn(),
                d.getProcesadaEn()
        );
    }
}
