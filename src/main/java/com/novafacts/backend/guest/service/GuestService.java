package com.novafacts.backend.guest.service;

import com.novafacts.backend.guest.dto.CreateGuestRequest;
import com.novafacts.backend.guest.dto.GuestResponse;
import com.novafacts.backend.guest.dto.UpdateGuestRequest;
import com.novafacts.backend.guest.entity.Guest;
import com.novafacts.backend.guest.repository.GuestRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class GuestService {

    private final GuestRepository guestRepository;

    public GuestService(GuestRepository guestRepository) {
        this.guestRepository = guestRepository;
    }

    @Transactional(readOnly = true)
    public List<GuestResponse> findAll() {
        return guestRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public GuestResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    @Transactional
    public GuestResponse create(CreateGuestRequest request) {
        if (guestRepository.existsByDocumentNumber(request.getDocumentNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un huésped con ese número de documento");
        }
        Guest guest = new Guest();
        guest.setFirstName(request.getFirstName());
        guest.setLastName(request.getLastName());
        guest.setDocumentType(request.getDocumentType());
        guest.setDocumentNumber(request.getDocumentNumber());
        guest.setEmail(request.getEmail());
        guest.setPhone(request.getPhone());
        return toResponse(guestRepository.save(guest));
    }

    @Transactional
    public GuestResponse update(Long id, UpdateGuestRequest request) {
        Guest guest = getOrThrow(id);
        if (guestRepository.existsByDocumentNumberAndIdNot(request.getDocumentNumber(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un huésped con ese número de documento");
        }
        guest.setFirstName(request.getFirstName());
        guest.setLastName(request.getLastName());
        guest.setDocumentType(request.getDocumentType());
        guest.setDocumentNumber(request.getDocumentNumber());
        guest.setEmail(request.getEmail());
        guest.setPhone(request.getPhone());
        return toResponse(guestRepository.save(guest));
    }

    @Transactional
    public void delete(Long id) {
        guestRepository.delete(getOrThrow(id));
    }

    private Guest getOrThrow(Long id) {
        return guestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Huésped no encontrado"));
    }

    private GuestResponse toResponse(Guest guest) {
        return new GuestResponse(
                guest.getId(),
                guest.getFirstName(),
                guest.getLastName(),
                guest.getDocumentType(),
                guest.getDocumentNumber(),
                guest.getEmail(),
                guest.getPhone(),
                guest.getCreatedAt()
        );
    }
}
