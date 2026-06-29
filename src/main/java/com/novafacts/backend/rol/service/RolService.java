package com.novafacts.backend.rol.service;

import com.novafacts.backend.rol.dto.RolResponse;
import com.novafacts.backend.rol.repository.RolRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RolService {

    private final RolRepository rolRepository;

    public RolService(RolRepository rolRepository) {
        this.rolRepository = rolRepository;
    }

    @Transactional(readOnly = true)
    public List<RolResponse> listar() {
        return rolRepository.findAll()
                .stream()
                .map(r -> new RolResponse(r.getId(), r.getNombre(), r.getDescripcion()))
                .toList();
    }
}
