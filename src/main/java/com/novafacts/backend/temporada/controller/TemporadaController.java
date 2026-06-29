package com.novafacts.backend.temporada.controller;

import com.novafacts.backend.temporada.dto.TemporadaRequest;
import com.novafacts.backend.temporada.dto.TemporadaResponse;
import com.novafacts.backend.temporada.service.TemporadaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/temporadas")
public class TemporadaController {

    private final TemporadaService temporadaService;

    public TemporadaController(TemporadaService temporadaService) {
        this.temporadaService = temporadaService;
    }

    @GetMapping
    public List<TemporadaResponse> listar() {
        return temporadaService.listar();
    }

    @GetMapping("/{id}")
    public TemporadaResponse buscarPorId(@PathVariable Integer id) {
        return temporadaService.buscarPorId(id);
    }

    @PostMapping
    public ResponseEntity<TemporadaResponse> crear(@Valid @RequestBody TemporadaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(temporadaService.crear(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TemporadaResponse> actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody TemporadaRequest request) {
        return ResponseEntity.ok(temporadaService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        temporadaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
