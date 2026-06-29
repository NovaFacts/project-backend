package com.novafacts.backend.temporada.service;

import com.novafacts.backend.temporada.dto.TemporadaRequest;
import com.novafacts.backend.temporada.dto.TemporadaResponse;
import com.novafacts.backend.temporada.entity.Temporada;
import com.novafacts.backend.temporada.repository.TemporadaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class TemporadaService {

    private final TemporadaRepository temporadaRepository;

    public TemporadaService(TemporadaRepository temporadaRepository) {
        this.temporadaRepository = temporadaRepository;
    }

    @Transactional(readOnly = true)
    public List<TemporadaResponse> listar() {
        return temporadaRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TemporadaResponse buscarPorId(Integer id) {
        return toResponse(getOrThrow(id));
    }

    @Transactional
    public TemporadaResponse crear(TemporadaRequest request) {
        validarFechas(request);
        Temporada temporada = new Temporada();
        temporada.setNombre(request.getNombre().strip());
        temporada.setFechaInicio(request.getFechaInicio());
        temporada.setFechaFin(request.getFechaFin());
        return toResponse(temporadaRepository.save(temporada));
    }

    @Transactional
    public TemporadaResponse actualizar(Integer id, TemporadaRequest request) {
        validarFechas(request);
        Temporada temporada = getOrThrow(id);
        temporada.setNombre(request.getNombre().strip());
        temporada.setFechaInicio(request.getFechaInicio());
        temporada.setFechaFin(request.getFechaFin());
        return toResponse(temporadaRepository.save(temporada));
    }

    @Transactional
    public void eliminar(Integer id) {
        temporadaRepository.delete(getOrThrow(id));
    }

    private void validarFechas(TemporadaRequest request) {
        if (!request.getFechaInicio().isBefore(request.getFechaFin())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de inicio debe ser anterior a la fecha de fin");
        }
    }

    private Temporada getOrThrow(Integer id) {
        return temporadaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Temporada no encontrada"));
    }

    private TemporadaResponse toResponse(Temporada t) {
        return new TemporadaResponse(t.getId(), t.getNombre(), t.getFechaInicio(), t.getFechaFin());
    }
}
