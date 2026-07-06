package com.novafacts.backend.factura.service;

import com.novafacts.backend.anticipo.entity.Anticipo;
import com.novafacts.backend.anticipo.entity.AnticipoEstado;
import com.novafacts.backend.anticipo.repository.AnticipoRepository;
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
import java.util.UUID;

@Service
public class FacturaService {

    private final FacturaRepository    facturaRepository;
    private final ReservationRepository reservationRepository;
    private final AnticipoRepository   anticipoRepository;
    private final UserRepository       userRepository;

    public FacturaService(FacturaRepository facturaRepository,
                          ReservationRepository reservationRepository,
                          AnticipoRepository anticipoRepository,
                          UserRepository userRepository) {
        this.facturaRepository    = facturaRepository;
        this.reservationRepository = reservationRepository;
        this.anticipoRepository   = anticipoRepository;
        this.userRepository       = userRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<FacturaResponse> findAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("emitidaEn").descending());
        return new PageResponse<>(facturaRepository.findAllWithUsuario(pageable).map(this::toResponse));
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
        BigDecimal total = subtotal
                .subtract(descuentoAnticipo)
                .add(recargoPenalidad)
                .add(impuestos)
                .setScale(2, RoundingMode.HALF_UP);

        // CRITICAL-2: if the caller identified the anticipo being deducted, mark it
        // APLICADO atomically within this transaction so that a subsequent refund via
        // DevolucionService cannot be created for the same advance payment.
        if (request.getAnticipoId() != null) {
            applyAnticipo(request.getAnticipoId(), reserva.getId(), descuentoAnticipo);
        }

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
        factura.setUrlDocumento(request.getUrlDocumento());

        return toResponse(facturaRepository.save(factura));
    }

    @Transactional
    public FacturaResponse emitir(Long id) {
        // C-2 (AUDIT_v5): lock the row before checking its estado, so a concurrent
        // anular() (or another emitir()) on the same factura cannot also read the
        // same PENDING state and both succeed with contradictory outcomes. The
        // second caller blocks here until the first transaction commits, then sees
        // the updated estado and correctly hits the CONFLICT check below instead.
        Factura factura = getForUpdateOrThrow(id);
        if (factura.getEstado() != InvoiceStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo se pueden emitir facturas en estado pendiente");
        }
        factura.setEstado(InvoiceStatus.PAID);
        return toResponse(facturaRepository.save(factura));
    }

    @Transactional
    public FacturaResponse anular(Long id) {
        // C-2 (AUDIT_v5): same reasoning as emitir() above.
        Factura factura = getForUpdateOrThrow(id);
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

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * Validates the anticipo and transitions it to APLICADO within the same
     * transaction as the Factura INSERT. Both writes commit or roll back together.
     */
    private void applyAnticipo(Long anticipoId, Long reservaId, BigDecimal descuentoAnticipo) {
        if (descuentoAnticipo.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El descuento por anticipo debe ser mayor a cero cuando se especifica un anticipoId");
        }

        // C-2: lock the anticipo row before checking its estado, so a concurrent
        // DevolucionService.create() call on the same anticipoId cannot also pass
        // the REGISTRADO check and refund what this transaction is about to apply.
        Anticipo anticipo = anticipoRepository.findByIdForUpdate(anticipoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Anticipo no encontrado"));

        if (!anticipo.getReserva().getId().equals(reservaId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El anticipo indicado no pertenece a la reserva que se está facturando");
        }

        if (anticipo.getEstado() != AnticipoEstado.REGISTRADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El anticipo ya fue aplicado a una factura o devuelto y no puede usarse de nuevo");
        }

        anticipo.setEstado(AnticipoEstado.APLICADO);
        anticipoRepository.save(anticipo);
    }

    private Factura getOrThrow(Long id) {
        return facturaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Factura no encontrada"));
    }

    /** C-2 (AUDIT_v5): same not-found message as getOrThrow(), but via the locked lookup. */
    private Factura getForUpdateOrThrow(Long id) {
        return facturaRepository.findByIdForUpdate(id)
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
