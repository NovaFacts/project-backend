package com.novafacts.backend.canal.controller;

import com.novafacts.backend.canal.dto.CanalRequest;
import com.novafacts.backend.canal.dto.CanalResponse;
import com.novafacts.backend.canal.service.CanalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/canales")
public class CanalController {

    private final CanalService canalService;

    public CanalController(CanalService canalService) {
        this.canalService = canalService;
    }

    @GetMapping
    public List<CanalResponse> listar() {
        return canalService.listar();
    }

    @GetMapping("/{id}")
    public CanalResponse buscarPorId(@PathVariable Integer id) {
        return canalService.buscarPorId(id);
    }

    @PostMapping
    public ResponseEntity<CanalResponse> crear(@Valid @RequestBody CanalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(canalService.crear(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CanalResponse> actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody CanalRequest request) {
        return ResponseEntity.ok(canalService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        canalService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
