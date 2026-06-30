package com.novafacts.backend.anticipo.controller;

import com.novafacts.backend.anticipo.dto.AnticipoRequest;
import com.novafacts.backend.anticipo.dto.AnticipoResponse;
import com.novafacts.backend.anticipo.service.AnticipoService;
import com.novafacts.backend.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/anticipos")
public class AnticipoController {

    private final AnticipoService anticipoService;

    public AnticipoController(AnticipoService anticipoService) {
        this.anticipoService = anticipoService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<AnticipoResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(anticipoService.findAll(page, Math.min(size, 100)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AnticipoResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(anticipoService.findById(id));
    }

    @GetMapping("/by-reserva/{reservaId}")
    public ResponseEntity<List<AnticipoResponse>> getByReserva(@PathVariable Long reservaId) {
        return ResponseEntity.ok(anticipoService.findByReservaId(reservaId));
    }

    @PostMapping
    public ResponseEntity<AnticipoResponse> create(@Valid @RequestBody AnticipoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(anticipoService.create(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        anticipoService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
