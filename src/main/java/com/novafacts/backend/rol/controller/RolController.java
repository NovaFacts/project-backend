package com.novafacts.backend.rol.controller;

import com.novafacts.backend.rol.dto.RolResponse;
import com.novafacts.backend.rol.service.RolService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
public class RolController {

    private final RolService rolService;

    public RolController(RolService rolService) {
        this.rolService = rolService;
    }

    @GetMapping
    public List<RolResponse> listar() {
        return rolService.listar();
    }
}
