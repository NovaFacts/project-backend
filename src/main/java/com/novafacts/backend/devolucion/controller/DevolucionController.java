package com.novafacts.backend.devolucion.controller;

import com.novafacts.backend.devolucion.dto.DevolucionRequest;
import com.novafacts.backend.devolucion.dto.DevolucionResponse;
import com.novafacts.backend.devolucion.service.DevolucionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/devoluciones")
public class DevolucionController {

    private final DevolucionService devolucionService;

    public DevolucionController(DevolucionService devolucionService) {
        this.devolucionService = devolucionService;
    }

    @GetMapping
    public ResponseEntity<List<DevolucionResponse>> getAll() {
        return ResponseEntity.ok(devolucionService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DevolucionResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(devolucionService.findById(id));
    }

    @GetMapping("/by-reserva/{reservaId}")
    public ResponseEntity<List<DevolucionResponse>> getByReservaId(@PathVariable Long reservaId) {
        return ResponseEntity.ok(devolucionService.findByReservaId(reservaId));
    }

    @PostMapping
    public ResponseEntity<DevolucionResponse> create(@Valid @RequestBody DevolucionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(devolucionService.create(request));
    }

    @PutMapping("/{id}/procesar")
    public ResponseEntity<DevolucionResponse> procesar(@PathVariable Long id) {
        return ResponseEntity.ok(devolucionService.procesar(id));
    }

    @PutMapping("/{id}/rechazar")
    public ResponseEntity<DevolucionResponse> rechazar(@PathVariable Long id) {
        return ResponseEntity.ok(devolucionService.rechazar(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        devolucionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
