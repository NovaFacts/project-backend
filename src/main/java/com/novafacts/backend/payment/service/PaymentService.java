package com.novafacts.backend.payment.service;

import com.novafacts.backend.invoice.entity.Invoice;
import com.novafacts.backend.invoice.entity.InvoiceStatus;
import com.novafacts.backend.invoice.repository.InvoiceRepository;
import com.novafacts.backend.invoice.service.InvoiceService;
import com.novafacts.backend.payment.dto.CreatePaymentRequest;
import com.novafacts.backend.payment.dto.PaymentResponse;
import com.novafacts.backend.payment.entity.Payment;
import com.novafacts.backend.payment.repository.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;

    public PaymentService(PaymentRepository paymentRepository,
                          InvoiceRepository invoiceRepository,
                          InvoiceService invoiceService) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceService = invoiceService;
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> findAll() {
        return paymentRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PaymentResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PaymentResponse findByInvoiceId(Long invoiceId) {
        return paymentRepository.findByInvoiceId(invoiceId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Pago no encontrado"));
    }

    @Transactional
    public PaymentResponse create(CreatePaymentRequest request) {
        // 1. Invoice must exist
        Invoice invoice = invoiceRepository.findById(request.getInvoiceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "La factura no existe"));

        // 2. Invoice must be PENDING
        if (invoice.getStatus() != InvoiceStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La factura no puede pagarse");
        }

        // 3. No existing payment for this invoice
        if (paymentRepository.existsByInvoiceId(request.getInvoiceId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La factura ya tiene un pago registrado");
        }

        // 4 & 5. Amount comes from invoice.total; paidAt is set now
        Payment payment = new Payment();
        payment.setInvoiceId(request.getInvoiceId());
        payment.setAmount(invoice.getTotal());
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setReference(request.getReference());
        payment.setPaidAt(LocalDateTime.now());
        Payment saved = paymentRepository.save(payment);

        // 6. Transition invoice to PAID within the same transaction
        invoiceService.pay(request.getInvoiceId());

        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        Payment payment = getOrThrow(id);
        Invoice invoice = invoiceRepository.findById(payment.getInvoiceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "La factura no existe"));
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede eliminar un pago confirmado");
        }
        paymentRepository.delete(payment);
    }

    private Payment getOrThrow(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Pago no encontrado"));
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getInvoiceId(),
                payment.getAmount(),
                payment.getPaymentMethod(),
                payment.getReference(),
                payment.getPaidAt(),
                payment.getCreatedAt()
        );
    }
}
