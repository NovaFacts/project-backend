package com.novafacts.backend.notacredito.service;

import com.novafacts.backend.auth.entity.User;
import com.novafacts.backend.auth.repository.UserRepository;
import com.novafacts.backend.factura.entity.Factura;
import com.novafacts.backend.factura.repository.FacturaRepository;
import com.novafacts.backend.notacredito.dto.NotaCreditoRequest;
import com.novafacts.backend.notacredito.dto.NotaCreditoResponse;
import com.novafacts.backend.notacredito.entity.NotaCredito;
import com.novafacts.backend.notacredito.repository.NotaCreditoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class NotaCreditoService {

    private final NotaCreditoRepository notaCreditoRepository;
    private final FacturaRepository facturaRepository;
    private final UserRepository userRepository;

    public NotaCreditoService(NotaCreditoRepository notaCreditoRepository,
                              FacturaRepository facturaRepository,
                              UserRepository userRepository) {
        this.notaCreditoRepository = notaCreditoRepository;
        this.facturaRepository     = facturaRepository;
        this.userRepository        = userRepository;
    }

    @Transactional(readOnly = true)
    public List<NotaCreditoResponse> findAll() {
        return notaCreditoRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public NotaCreditoResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<NotaCreditoResponse> findByFacturaId(Long facturaId) {
        return notaCreditoRepository.findByFacturaId(facturaId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public NotaCreditoResponse create(NotaCreditoRequest request) {
        Factura factura = facturaRepository.findById(request.getFacturaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Factura no encontrada"));

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User usuario = userRepository.findByUsername(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Usuario autenticado no encontrado en el sistema"));

        BigDecimal monto = request.getMonto().setScale(2, RoundingMode.HALF_UP);
        String numeroNota = "NC-" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 8).toUpperCase();

        NotaCredito nota = new NotaCredito();
        nota.setFactura(factura);
        nota.setUsuario(usuario);
        nota.setNumeroNota(numeroNota);
        nota.setMonto(monto);
        nota.setMotivo(request.getMotivo());

        return toResponse(notaCreditoRepository.save(nota));
    }

    @Transactional
    public void delete(Long id) {
        notaCreditoRepository.delete(getOrThrow(id));
    }

    private NotaCredito getOrThrow(Long id) {
        return notaCreditoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Nota de crédito no encontrada"));
    }

    private NotaCreditoResponse toResponse(NotaCredito nc) {
        return new NotaCreditoResponse(
                nc.getId(),
                nc.getFactura().getId(),
                nc.getFactura().getNumeroFactura(),
                nc.getUsuario().getId(),
                nc.getUsuario().getNombre(),
                nc.getNumeroNota(),
                nc.getMonto(),
                nc.getMotivo(),
                nc.getEmitidaEn()
        );
    }
}
