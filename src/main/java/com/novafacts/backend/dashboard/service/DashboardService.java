package com.novafacts.backend.dashboard.service;

import com.novafacts.backend.anticipo.repository.AnticipoRepository;
import com.novafacts.backend.dashboard.dto.DashboardResponse;
import com.novafacts.backend.factura.repository.FacturaRepository;
import com.novafacts.backend.invoice.entity.InvoiceStatus;
import com.novafacts.backend.property.repository.PropertyRepository;
import com.novafacts.backend.reservation.entity.ReservationStatus;
import com.novafacts.backend.reservation.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class DashboardService {

    private final PropertyRepository propertyRepository;
    private final ReservationRepository reservationRepository;
    private final FacturaRepository facturaRepository;
    private final AnticipoRepository anticipoRepository;

    public DashboardService(PropertyRepository propertyRepository,
                            ReservationRepository reservationRepository,
                            FacturaRepository facturaRepository,
                            AnticipoRepository anticipoRepository) {
        this.propertyRepository    = propertyRepository;
        this.reservationRepository = reservationRepository;
        this.facturaRepository     = facturaRepository;
        this.anticipoRepository    = anticipoRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getSummary() {
        BigDecimal rawMonto = anticipoRepository.sumTotalMonto();
        BigDecimal montoTotalAnticipos = rawMonto != null ? rawMonto : BigDecimal.ZERO;

        return new DashboardResponse(
                reservationRepository.count(),
                propertyRepository.count(),
                reservationRepository.countByStatus(ReservationStatus.CONFIRMED),
                reservationRepository.countByStatus(ReservationStatus.CANCELLED),
                reservationRepository.countByStatus(ReservationStatus.COMPLETED),
                facturaRepository.countByEstado(InvoiceStatus.PENDING),
                facturaRepository.countByEstado(InvoiceStatus.PAID),
                facturaRepository.countByEstado(InvoiceStatus.CANCELLED),
                anticipoRepository.count(),
                montoTotalAnticipos
        );
    }
}
