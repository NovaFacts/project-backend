package com.novafacts.backend.property.service;

import com.novafacts.backend.property.dto.CreatePropertyRequest;
import com.novafacts.backend.property.dto.PropertyResponse;
import com.novafacts.backend.property.dto.UpdatePropertyRequest;
import com.novafacts.backend.property.entity.Property;
import com.novafacts.backend.property.repository.PropertyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class PropertyService {

    private final PropertyRepository propertyRepository;

    public PropertyService(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    @Transactional(readOnly = true)
    public List<PropertyResponse> findAll() {
        return propertyRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PropertyResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    @Transactional
    public PropertyResponse create(CreatePropertyRequest request) {
        String name = request.getName().strip();

        if (propertyRepository.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una propiedad con ese nombre");
        }
        Property property = new Property();
        property.setName(name);
        property.setAddress(request.getAddress());
        property.setDescripcion(request.getDescripcion());
        property.setActiva(true);
        return toResponse(propertyRepository.save(property));
    }

    @Transactional
    public PropertyResponse update(Long id, UpdatePropertyRequest request) {
        Property property = getOrThrow(id);
        String name = request.getName().strip();

        if (propertyRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una propiedad con ese nombre");
        }
        property.setName(name);
        property.setAddress(request.getAddress());
        property.setDescripcion(request.getDescripcion());
        property.setActiva(request.getActiva());
        return toResponse(propertyRepository.save(property));
    }

    @Transactional
    public void delete(Long id) {
        Property property = getOrThrow(id);
        property.setActiva(false);
        propertyRepository.save(property);
    }

    private Property getOrThrow(Long id) {
        return propertyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Propiedad no encontrada"));
    }

    private PropertyResponse toResponse(Property property) {
        return new PropertyResponse(
                property.getId(),
                property.getName(),
                property.getAddress(),
                property.getDescripcion(),
                property.getActiva()
        );
    }
}
