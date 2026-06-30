package com.novafacts.backend.notacredito.controller;

import com.novafacts.backend.common.PageResponse;
import com.novafacts.backend.notacredito.dto.NotaCreditoRequest;
import com.novafacts.backend.notacredito.dto.NotaCreditoResponse;
import com.novafacts.backend.notacredito.service.NotaCreditoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notas-credito")
public class NotaCreditoController {

    private final NotaCreditoService notaCreditoService;

    public NotaCreditoController(NotaCreditoService notaCreditoService) {
        this.notaCreditoService = notaCreditoService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<NotaCreditoResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(notaCreditoService.findAll(page, Math.min(size, 100)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotaCreditoResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(notaCreditoService.findById(id));
    }

    @GetMapping("/by-factura/{facturaId}")
    public ResponseEntity<List<NotaCreditoResponse>> getByFacturaId(@PathVariable Long facturaId) {
        return ResponseEntity.ok(notaCreditoService.findByFacturaId(facturaId));
    }

    @PostMapping
    public ResponseEntity<NotaCreditoResponse> create(@Valid @RequestBody NotaCreditoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(notaCreditoService.create(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        notaCreditoService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
