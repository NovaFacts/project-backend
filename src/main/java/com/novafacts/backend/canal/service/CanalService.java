package com.novafacts.backend.canal.service;

import com.novafacts.backend.canal.dto.CanalRequest;
import com.novafacts.backend.canal.dto.CanalResponse;
import com.novafacts.backend.canal.entity.Canal;
import com.novafacts.backend.canal.repository.CanalRepository;
import com.novafacts.backend.reservation.repository.ReservationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CanalService {

    private final CanalRepository       canalRepository;
    private final ReservationRepository reservationRepository;

    public CanalService(CanalRepository canalRepository,
                        ReservationRepository reservationRepository) {
        this.canalRepository      = canalRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional(readOnly = true)
    public List<CanalResponse> listar() {
        return canalRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CanalResponse buscarPorId(Integer id) {
        return toResponse(getOrThrow(id));
    }

    @Transactional
    public CanalResponse crear(CanalRequest request) {
        validarNombreUnico(request);
        Canal canal = new Canal();
        canal.setNombre(request.getNombre().strip());
        canal.setTipo(request.getTipo().strip());
        return toResponse(canalRepository.save(canal));
    }

    @Transactional
    public CanalResponse actualizar(Integer id, CanalRequest request) {
        validarNombreUnico(id, request);
        Canal canal = getOrThrow(id);
        canal.setNombre(request.getNombre().strip());
        canal.setTipo(request.getTipo().strip());
        return toResponse(canalRepository.save(canal));
    }

    @Transactional
    public void eliminar(Integer id) {
        Canal canal = getOrThrow(id);
        if (reservationRepository.existsByCanalId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede eliminar el canal porque existen reservas que lo referencian.");
        }
        canalRepository.delete(canal);
    }

    private void validarNombreUnico(CanalRequest request) {
        if (canalRepository.existsByNombreIgnoreCase(request.getNombre().strip())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un canal con ese nombre");
        }
    }

    private void validarNombreUnico(Integer id, CanalRequest request) {
        if (canalRepository.existsByNombreIgnoreCaseAndIdNot(request.getNombre().strip(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un canal con ese nombre");
        }
    }

    private Canal getOrThrow(Integer id) {
        return canalRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Canal no encontrado"));
    }

    private CanalResponse toResponse(Canal c) {
        return new CanalResponse(c.getId(), c.getNombre(), c.getTipo());
    }
}
