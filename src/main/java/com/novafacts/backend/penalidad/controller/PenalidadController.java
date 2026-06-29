package com.novafacts.backend.penalidad.controller;

import com.novafacts.backend.penalidad.dto.PenalidadRequest;
import com.novafacts.backend.penalidad.dto.PenalidadResponse;
import com.novafacts.backend.penalidad.service.PenalidadService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/penalidades")
public class PenalidadController {

    private final PenalidadService penalidadService;

    public PenalidadController(PenalidadService penalidadService) {
        this.penalidadService = penalidadService;
    }

    @GetMapping
    public ResponseEntity<List<PenalidadResponse>> getAll() {
        return ResponseEntity.ok(penalidadService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PenalidadResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(penalidadService.findById(id));
    }

    @GetMapping("/by-reserva/{reservaId}")
    public ResponseEntity<List<PenalidadResponse>> getByReserva(@PathVariable Long reservaId) {
        return ResponseEntity.ok(penalidadService.findByReservaId(reservaId));
    }

    @PostMapping
    public ResponseEntity<PenalidadResponse> create(@Valid @RequestBody PenalidadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(penalidadService.create(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        penalidadService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
