package com.novafacts.backend.temporada.service;

import com.novafacts.backend.reservation.repository.ReservationRepository;
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

    private final TemporadaRepository  temporadaRepository;
    private final ReservationRepository reservationRepository;

    public TemporadaService(TemporadaRepository temporadaRepository,
                            ReservationRepository reservationRepository) {
        this.temporadaRepository  = temporadaRepository;
        this.reservationRepository = reservationRepository;
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
        validarSinSuperposicion(request);
        validarNombreUnico(request);
        Temporada temporada = new Temporada();
        temporada.setNombre(request.getNombre().strip());
        temporada.setFechaInicio(request.getFechaInicio());
        temporada.setFechaFin(request.getFechaFin());
        return toResponse(temporadaRepository.save(temporada));
    }

    @Transactional
    public TemporadaResponse actualizar(Integer id, TemporadaRequest request) {
        validarFechas(request);
        validarSinSuperposicion(id, request);
        validarNombreUnico(id, request);
        Temporada temporada = getOrThrow(id);
        temporada.setNombre(request.getNombre().strip());
        temporada.setFechaInicio(request.getFechaInicio());
        temporada.setFechaFin(request.getFechaFin());
        return toResponse(temporadaRepository.save(temporada));
    }

    @Transactional
    public void eliminar(Integer id) {
        Temporada temporada = getOrThrow(id);
        if (reservationRepository.existsByTemporadaId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede eliminar la temporada porque existen reservas que la referencian.");
        }
        temporadaRepository.delete(temporada);
    }

    private void validarFechas(TemporadaRequest request) {
        if (!request.getFechaInicio().isBefore(request.getFechaFin())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de inicio debe ser anterior a la fecha de fin");
        }
    }

    private void validarSinSuperposicion(TemporadaRequest request) {
        if (temporadaRepository.existsOverlap(request.getFechaInicio(), request.getFechaFin())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El rango de fechas se superpone con una temporada existente");
        }
    }

    private void validarSinSuperposicion(Integer id, TemporadaRequest request) {
        if (temporadaRepository.existsOverlapExcludingId(
                request.getFechaInicio(), request.getFechaFin(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El rango de fechas se superpone con una temporada existente");
        }
    }

    private void validarNombreUnico(TemporadaRequest request) {
        if (temporadaRepository.existsByNombreIgnoreCase(request.getNombre().strip())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una temporada con ese nombre");
        }
    }

    private void validarNombreUnico(Integer id, TemporadaRequest request) {
        if (temporadaRepository.existsByNombreIgnoreCaseAndIdNot(request.getNombre().strip(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una temporada con ese nombre");
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
