package com.novafacts.backend.invoice.service;

import com.novafacts.backend.invoice.dto.CreateInvoiceRequest;
import com.novafacts.backend.invoice.dto.InvoiceResponse;
import com.novafacts.backend.invoice.entity.Invoice;
import com.novafacts.backend.invoice.entity.InvoiceStatus;
import com.novafacts.backend.invoice.repository.InvoiceRepository;
import com.novafacts.backend.reservation.entity.Reservation;
import com.novafacts.backend.reservation.entity.ReservationStatus;
import com.novafacts.backend.reservation.repository.ReservationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class InvoiceService {

    private static final BigDecimal IVA_RATE = new BigDecimal("0.19");

    private final InvoiceRepository invoiceRepository;
    private final ReservationRepository reservationRepository;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          ReservationRepository reservationRepository) {
        this.invoiceRepository = invoiceRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> findAll() {
        return invoiceRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public InvoiceResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse findByReservationId(Long reservationId) {
        return invoiceRepository.findByReservationId(reservationId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Factura no encontrada"));
    }

    @Transactional
    public InvoiceResponse create(CreateInvoiceRequest request) {
        Reservation reservation = reservationRepository.findById(request.getReservationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Reserva no encontrada"));

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se puede facturar una reserva cancelada");
        }

        if (invoiceRepository.existsByReservationId(request.getReservationId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una factura para esta reserva");
        }

        BigDecimal subtotal = request.getSubtotal().setScale(2, RoundingMode.HALF_UP);
        BigDecimal tax      = subtotal.multiply(IVA_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total    = subtotal.add(tax).setScale(2, RoundingMode.HALF_UP);

        Invoice invoice = new Invoice();
        invoice.setReservationId(request.getReservationId());
        invoice.setSubtotal(subtotal);
        invoice.setTax(tax);
        invoice.setTotal(total);
        invoice.setStatus(InvoiceStatus.PENDING);
        return toResponse(invoiceRepository.save(invoice));
    }

    @Transactional
    public InvoiceResponse pay(Long id) {
        Invoice invoice = getOrThrow(id);
        if (invoice.getStatus() != InvoiceStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo se pueden pagar facturas pendientes");
        }
        invoice.setStatus(InvoiceStatus.PAID);
        return toResponse(invoiceRepository.save(invoice));
    }

    @Transactional
    public InvoiceResponse cancel(Long id) {
        Invoice invoice = getOrThrow(id);
        if (invoice.getStatus() != InvoiceStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo se pueden cancelar facturas pendientes");
        }
        invoice.setStatus(InvoiceStatus.CANCELLED);
        return toResponse(invoiceRepository.save(invoice));
    }

    @Transactional
    public void delete(Long id) {
        Invoice invoice = getOrThrow(id);
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede eliminar una factura pagada");
        }
        invoiceRepository.delete(invoice);
    }

    private Invoice getOrThrow(Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Factura no encontrada"));
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getReservationId(),
                invoice.getSubtotal(),
                invoice.getTax(),
                invoice.getTotal(),
                invoice.getStatus(),
                invoice.getCreatedAt()
        );
    }
}
