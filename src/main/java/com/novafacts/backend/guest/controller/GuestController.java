package com.novafacts.backend.guest.controller;

import com.novafacts.backend.guest.dto.CreateGuestRequest;
import com.novafacts.backend.guest.dto.GuestResponse;
import com.novafacts.backend.guest.dto.UpdateGuestRequest;
import com.novafacts.backend.guest.service.GuestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/guests")
public class GuestController {

    private final GuestService guestService;

    public GuestController(GuestService guestService) {
        this.guestService = guestService;
    }

    @GetMapping
    public List<GuestResponse> getAll() {
        return guestService.findAll();
    }

    @GetMapping("/{id}")
    public GuestResponse getById(@PathVariable Long id) {
        return guestService.findById(id);
    }

    @PostMapping
    public ResponseEntity<GuestResponse> create(@Valid @RequestBody CreateGuestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(guestService.create(request));
    }

    @PutMapping("/{id}")
    public GuestResponse update(@PathVariable Long id, @Valid @RequestBody UpdateGuestRequest request) {
        return guestService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        guestService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
