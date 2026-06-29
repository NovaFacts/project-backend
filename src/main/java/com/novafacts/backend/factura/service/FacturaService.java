package com.novafacts.backend.factura.service;

import com.novafacts.backend.auth.entity.User;
import com.novafacts.backend.auth.repository.UserRepository;
import com.novafacts.backend.factura.dto.FacturaRequest;
import com.novafacts.backend.factura.dto.FacturaResponse;
import com.novafacts.backend.factura.entity.Factura;
import com.novafacts.backend.factura.repository.FacturaRepository;
import com.novafacts.backend.invoice.entity.InvoiceStatus;
import com.novafacts.backend.reservation.entity.Reservation;
import com.novafacts.backend.reservation.entity.ReservationStatus;
import com.novafacts.backend.reservation.repository.ReservationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class FacturaService {

    private final FacturaRepository facturaRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;

    public FacturaService(FacturaRepository facturaRepository,
                          ReservationRepository reservationRepository,
                          UserRepository userRepository) {
        this.facturaRepository   = facturaRepository;
        this.reservationRepository = reservationRepository;
        this.userRepository      = userRepository;
    }

    @Transactional(readOnly = true)
    public List<FacturaResponse> findAll() {
        return facturaRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public FacturaResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public FacturaResponse findByReservaId(Long reservaId) {
        return facturaRepository.findByReservaId(reservaId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Factura no encontrada para la reserva indicada"));
    }

    @Transactional
    public FacturaResponse create(FacturaRequest request) {
        Reservation reserva = reservationRepository.findById(request.getReservaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Reserva no encontrada"));

        if (reserva.getStatus() == ReservationStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se puede facturar una reserva cancelada");
        }

        if (facturaRepository.existsByReservaId(request.getReservaId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una factura para esta reserva");
        }

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User usuario = userRepository.findByUsername(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Usuario autenticado no encontrado en el sistema"));

        BigDecimal subtotal          = request.getSubtotal().setScale(2, RoundingMode.HALF_UP);
        BigDecimal descuentoAnticipo = nullSafe(request.getDescuentoAnticipo());
        BigDecimal recargoPenalidad  = nullSafe(request.getRecargoPenalidad());
        BigDecimal impuestos         = nullSafe(request.getImpuestos());
        // Backend enforces calculation — frontend total is ignored
        BigDecimal total = subtotal
                .subtract(descuentoAnticipo)
                .add(recargoPenalidad)
                .add(impuestos)
                .setScale(2, RoundingMode.HALF_UP);

        String numeroFactura = "FAC-" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 8).toUpperCase();

        Factura factura = new Factura();
        factura.setReserva(reserva);
        factura.setUsuario(usuario);
        factura.setNumeroFactura(numeroFactura);
        factura.setSubtotal(subtotal);
        factura.setDescuentoAnticipo(descuentoAnticipo);
        factura.setRecargoPenalidad(recargoPenalidad);
        factura.setImpuestos(impuestos);
        factura.setTotal(total);
        factura.setEstado(InvoiceStatus.PENDING);

        return toResponse(facturaRepository.save(factura));
    }

    @Transactional
    public FacturaResponse emitir(Long id) {
        Factura factura = getOrThrow(id);
        if (factura.getEstado() != InvoiceStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo se pueden emitir facturas en estado pendiente");
        }
        factura.setEstado(InvoiceStatus.PAID);
        return toResponse(facturaRepository.save(factura));
    }

    @Transactional
    public FacturaResponse anular(Long id) {
        Factura factura = getOrThrow(id);
        if (factura.getEstado() != InvoiceStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo se pueden anular facturas en estado pendiente");
        }
        factura.setEstado(InvoiceStatus.CANCELLED);
        return toResponse(facturaRepository.save(factura));
    }

    @Transactional
    public void delete(Long id) {
        Factura factura = getOrThrow(id);
        if (factura.getEstado() == InvoiceStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede eliminar una factura ya emitida");
        }
        facturaRepository.delete(factura);
    }

    private Factura getOrThrow(Long id) {
        return facturaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Factura no encontrada"));
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return (value != null ? value : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private FacturaResponse toResponse(Factura f) {
        return new FacturaResponse(
                f.getId(),
                f.getReserva().getId(),
                f.getUsuario().getId(),
                f.getUsuario().getNombre(),
                f.getNumeroFactura(),
                f.getSubtotal(),
                f.getDescuentoAnticipo(),
                f.getRecargoPenalidad(),
                f.getImpuestos(),
                f.getTotal(),
                f.getEstado(),
                f.getUrlDocumento(),
                f.getEmitidaEn()
        );
    }
}
