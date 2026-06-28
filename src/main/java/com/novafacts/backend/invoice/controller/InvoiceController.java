package com.novafacts.backend.invoice.controller;

import com.novafacts.backend.invoice.dto.CreateInvoiceRequest;
import com.novafacts.backend.invoice.dto.InvoiceResponse;
import com.novafacts.backend.invoice.service.InvoiceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping
    public ResponseEntity<List<InvoiceResponse>> getAll() {
        return ResponseEntity.ok(invoiceService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(invoiceService.findById(id));
    }

    @GetMapping("/by-reservation/{reservationId}")
    public ResponseEntity<InvoiceResponse> getByReservationId(@PathVariable Long reservationId) {
        return ResponseEntity.ok(invoiceService.findByReservationId(reservationId));
    }

    @PostMapping
    public ResponseEntity<InvoiceResponse> create(@Valid @RequestBody CreateInvoiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(invoiceService.create(request));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<InvoiceResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(invoiceService.cancel(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        invoiceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
