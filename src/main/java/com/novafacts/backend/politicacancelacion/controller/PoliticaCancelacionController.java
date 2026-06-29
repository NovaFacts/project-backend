package com.novafacts.backend.politicacancelacion.controller;

import com.novafacts.backend.politicacancelacion.dto.PoliticaCancelacionRequest;
import com.novafacts.backend.politicacancelacion.dto.PoliticaCancelacionResponse;
import com.novafacts.backend.politicacancelacion.service.PoliticaCancelacionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/politicas")
public class PoliticaCancelacionController {

    private final PoliticaCancelacionService politicaService;

    public PoliticaCancelacionController(PoliticaCancelacionService politicaService) {
        this.politicaService = politicaService;
    }

    @GetMapping
    public List<PoliticaCancelacionResponse> getAll() {
        return politicaService.listar();
    }

    @GetMapping("/{id}")
    public PoliticaCancelacionResponse getById(@PathVariable Integer id) {
        return politicaService.buscarPorId(id);
    }

    @GetMapping("/propiedad/{propiedadId}")
    public List<PoliticaCancelacionResponse> getByPropiedad(@PathVariable Long propiedadId) {
        return politicaService.listarPorPropiedad(propiedadId);
    }

    @PostMapping
    public ResponseEntity<PoliticaCancelacionResponse> create(
            @Valid @RequestBody PoliticaCancelacionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(politicaService.crear(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PoliticaCancelacionResponse> update(
            @PathVariable Integer id,
            @Valid @RequestBody PoliticaCancelacionRequest request) {
        return ResponseEntity.ok(politicaService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        politicaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
