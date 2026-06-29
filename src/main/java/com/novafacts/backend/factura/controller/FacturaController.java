package com.novafacts.backend.factura.controller;

import com.novafacts.backend.common.PageResponse;
import com.novafacts.backend.factura.dto.FacturaRequest;
import com.novafacts.backend.factura.dto.FacturaResponse;
import com.novafacts.backend.factura.service.FacturaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/facturas")
public class FacturaController {

    private final FacturaService facturaService;

    public FacturaController(FacturaService facturaService) {
        this.facturaService = facturaService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<FacturaResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(facturaService.findAll(page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FacturaResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(facturaService.findById(id));
    }

    @GetMapping("/by-reserva/{reservaId}")
    public ResponseEntity<FacturaResponse> getByReservaId(@PathVariable Long reservaId) {
        return ResponseEntity.ok(facturaService.findByReservaId(reservaId));
    }

    @PostMapping
    public ResponseEntity<FacturaResponse> create(@Valid @RequestBody FacturaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(facturaService.create(request));
    }

    @PutMapping("/{id}/emitir")
    public ResponseEntity<FacturaResponse> emitir(@PathVariable Long id) {
        return ResponseEntity.ok(facturaService.emitir(id));
    }

    @PutMapping("/{id}/anular")
    public ResponseEntity<FacturaResponse> anular(@PathVariable Long id) {
        return ResponseEntity.ok(facturaService.anular(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        facturaService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
