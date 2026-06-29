package com.novafacts.backend.canal.service;

import com.novafacts.backend.canal.dto.CanalResponse;
import com.novafacts.backend.canal.repository.CanalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CanalService {

    private final CanalRepository canalRepository;

    public CanalService(CanalRepository canalRepository) {
        this.canalRepository = canalRepository;
    }

    @Transactional(readOnly = true)
    public List<CanalResponse> listar() {
        return canalRepository.findAll().stream()
                .map(c -> new CanalResponse(c.getId(), c.getNombre(), c.getTipo()))
                .toList();
    }
}
