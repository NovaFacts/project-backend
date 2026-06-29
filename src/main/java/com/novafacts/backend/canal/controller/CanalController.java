package com.novafacts.backend.canal.controller;

import com.novafacts.backend.canal.dto.CanalResponse;
import com.novafacts.backend.canal.service.CanalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
