package com.novafacts.backend.politicacancelacion.service;

import com.novafacts.backend.politicacancelacion.dto.PoliticaCancelacionRequest;
import com.novafacts.backend.politicacancelacion.dto.PoliticaCancelacionResponse;
import com.novafacts.backend.politicacancelacion.entity.PoliticaCancelacion;
import com.novafacts.backend.politicacancelacion.repository.PoliticaCancelacionRepository;
import com.novafacts.backend.property.entity.Property;
import com.novafacts.backend.property.repository.PropertyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class PoliticaCancelacionService {

    private final PoliticaCancelacionRepository politicaRepository;
    private final PropertyRepository propertyRepository;

    public PoliticaCancelacionService(PoliticaCancelacionRepository politicaRepository,
                                      PropertyRepository propertyRepository) {
        this.politicaRepository = politicaRepository;
        this.propertyRepository = propertyRepository;
    }

    @Transactional(readOnly = true)
    public List<PoliticaCancelacionResponse> listar() {
        return politicaRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PoliticaCancelacionResponse buscarPorId(Integer id) {
        return toResponse(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<PoliticaCancelacionResponse> listarPorPropiedad(Long propiedadId) {
        return politicaRepository.findByPropiedadId(propiedadId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public PoliticaCancelacionResponse crear(PoliticaCancelacionRequest request) {
        Property propiedad = getPropertyOrThrow(request.getPropiedadId());
        PoliticaCancelacion politica = new PoliticaCancelacion();
        politica.setPropiedad(propiedad);
        politica.setNombre(request.getNombre());
        politica.setDescripcion(request.getDescripcion());
        politica.setPorcentajeReembolso(request.getPorcentajeReembolso());
        politica.setDiasAviso(request.getDiasAviso());
        return toResponse(politicaRepository.save(politica));
    }

    @Transactional
    public PoliticaCancelacionResponse actualizar(Integer id, PoliticaCancelacionRequest request) {
        PoliticaCancelacion politica = getOrThrow(id);
        Property propiedad = getPropertyOrThrow(request.getPropiedadId());
        politica.setPropiedad(propiedad);
        politica.setNombre(request.getNombre());
        politica.setDescripcion(request.getDescripcion());
        politica.setPorcentajeReembolso(request.getPorcentajeReembolso());
        politica.setDiasAviso(request.getDiasAviso());
        return toResponse(politicaRepository.save(politica));
    }

    @Transactional
    public void eliminar(Integer id) {
        // FK violation from reserva.politica_cancelacion_id propagates as
        // DataIntegrityViolationException → GlobalExceptionHandler → 409
        politicaRepository.delete(getOrThrow(id));
    }

    private PoliticaCancelacion getOrThrow(Integer id) {
        return politicaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Política de cancelación no encontrada"));
    }

    private Property getPropertyOrThrow(Long propiedadId) {
        return propertyRepository.findById(propiedadId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Propiedad no encontrada"));
    }

    private PoliticaCancelacionResponse toResponse(PoliticaCancelacion p) {
        return new PoliticaCancelacionResponse(
                p.getId(),
                p.getPropiedad().getId(),
                p.getPropiedad().getName(),
                p.getNombre(),
                p.getDescripcion(),
                p.getPorcentajeReembolso(),
                p.getDiasAviso()
        );
    }
}
