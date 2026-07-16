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
import com.novafacts.backend.penalidad.entity.Penalidad;
import com.novafacts.backend.penalidad.repository.PenalidadRepository;
import com.novafacts.backend.reservation.entity.Reservation;
import com.novafacts.backend.reservation.entity.ReservationStatus;
import com.novafacts.backend.reservation.repository.ReservationRepository;
import com.novafacts.backend.common.PageResponse;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.UUID;

@Service
public class FacturaService {

    private final FacturaRepository    facturaRepository;
    private final ReservationRepository reservationRepository;
    private final AnticipoRepository   anticipoRepository;
    private final PenalidadRepository  penalidadRepository;
    private final UserRepository       userRepository;

    // Phase 4: the single, project-wide documented IVA rate (RF_16: "impuestos = base x
    // 0.19"), externalized so it can change without a code change — mirrors the existing
    // @Value-on-a-field pattern already used for scalar config (see SecurityConfig.allowedOrigins).
    @Value("${app.iva-rate:0.19}")
    private BigDecimal ivaRate;

    public FacturaService(FacturaRepository facturaRepository,
                          ReservationRepository reservationRepository,
                          AnticipoRepository anticipoRepository,
                          PenalidadRepository penalidadRepository,
                          UserRepository userRepository) {
        this.facturaRepository    = facturaRepository;
        this.reservationRepository = reservationRepository;
        this.anticipoRepository   = anticipoRepository;
        this.penalidadRepository  = penalidadRepository;
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

        // Phase 3: subtotal is never taken from the client — it is always the reservation's
        // own persisted, negotiated amount (RF_16: "subtotal = reserva.monto_total"), read
        // from the same Reservation entity already loaded above, no extra query needed.
        BigDecimal subtotal          = reserva.getMontoTotal().setScale(2, RoundingMode.HALF_UP);
        // RF11/RF12: the discount is never taken from the client — it is the sum of every
        // anticipo currently REGISTRADO on this reservation, computed and applied here,
        // atomically, in the same transaction as the Factura insert (see applyRegisteredAnticipos).
        BigDecimal descuentoAnticipo = applyRegisteredAnticipos(reserva.getId());
        // Penalidad aggregation: the client never supplies this either — it is looked up
        // directly from the reservation's own approved Penalidad (see determineRecargoPenalidad).
        BigDecimal recargoPenalidad  = determineRecargoPenalidad(reserva.getId());
        // Phase 4: impuestos is never taken from the client — it is always base x app.iva-rate,
        // the same formula documented project-wide (RF_16), computed from the authoritative
        // values above rather than anything the request could supply.
        BigDecimal base = subtotal.subtract(descuentoAnticipo).add(recargoPenalidad);
        BigDecimal impuestos = base.multiply(ivaRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = base.add(impuestos).setScale(2, RoundingMode.HALF_UP);

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
     * RF11/RF12: retrieves every anticipo currently REGISTRADO for this reservation,
     * locks and re-checks each one individually (reusing the existing single-row
     * pessimistic-lock method already relied on by DevolucionService, rather than
     * introducing a new bulk-locking query), marks it APLICADO, and returns the sum
     * of their montos as the invoice's descuentoAnticipo. All of this happens inside
     * the same @Transactional method as the Factura INSERT (see create()), so either
     * every anticipo mutation and the invoice are persisted together, or — if
     * anything downstream fails — none of it is.
     *
     * The unlocked id lookup and the per-id lock are two separate steps on purpose:
     * a concurrent transaction may apply or refund one of these anticipos between the
     * two steps, so each is re-checked for REGISTRADO immediately after its lock is
     * acquired, and silently excluded from this invoice's total if it no longer
     * qualifies — the same anticipo can never end up counted by two transactions.
     */
    private BigDecimal applyRegisteredAnticipos(Long reservaId) {
        List<Long> anticipoIds = anticipoRepository.findIdsByReservaIdAndEstado(reservaId, AnticipoEstado.REGISTRADO);

        BigDecimal total = BigDecimal.ZERO;
        for (Long anticipoId : anticipoIds) {
            Anticipo anticipo = anticipoRepository.findByIdForUpdate(anticipoId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Anticipo no encontrado"));

            if (anticipo.getEstado() != AnticipoEstado.REGISTRADO) {
                continue;
            }

            anticipo.setEstado(AnticipoEstado.APLICADO);
            anticipoRepository.save(anticipo);
            total = total.add(anticipo.getMonto());
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Penalidad aggregation: unlike Anticipo, a reservation can have at most one Penalidad —
     * enforced by PenalidadService.create()'s existsByReservaId guard (C-1, AUDIT_v5), proven
     * by PenalidadControllerTest#duplicate_penalidad_returns_409_and_does_not_overcharge. So
     * there is nothing to lock or mutate here: Penalidad has no lifecycle state (no "estado"
     * field, no PUT/PATCH endpoint — it is immutable once created), so reading its
     * montoAprobado is a plain, safe lookup. Summing the (0 or 1) results, rather than
     * assuming exactly one, keeps this correct even if that invariant were ever relaxed.
     */
    private BigDecimal determineRecargoPenalidad(Long reservaId) {
        return penalidadRepository.findByReservaId(reservaId).stream()
                .map(Penalidad::getMontoAprobado)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
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
