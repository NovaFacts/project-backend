package com.novafacts.backend.dashboard.service;

import com.novafacts.backend.dashboard.dto.DashboardResponse;
import com.novafacts.backend.guest.repository.GuestRepository;
import com.novafacts.backend.invoice.entity.InvoiceStatus;
import com.novafacts.backend.invoice.repository.InvoiceRepository;
import com.novafacts.backend.payment.repository.PaymentRepository;
import com.novafacts.backend.property.repository.PropertyRepository;
import com.novafacts.backend.reservation.entity.ReservationStatus;
import com.novafacts.backend.reservation.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class DashboardService {

    private final GuestRepository guestRepository;
    private final PropertyRepository propertyRepository;
    private final ReservationRepository reservationRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;

    public DashboardService(GuestRepository guestRepository,
                            PropertyRepository propertyRepository,
                            ReservationRepository reservationRepository,
                            InvoiceRepository invoiceRepository,
                            PaymentRepository paymentRepository) {
        this.guestRepository = guestRepository;
        this.propertyRepository = propertyRepository;
        this.reservationRepository = reservationRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getSummary() {
        BigDecimal rawRevenue = paymentRepository.sumTotalRevenue();
        BigDecimal totalRevenue = rawRevenue != null ? rawRevenue : BigDecimal.ZERO;

        return new DashboardResponse(
                guestRepository.count(),
                propertyRepository.count(),
                reservationRepository.countByStatus(ReservationStatus.CONFIRMED),
                reservationRepository.countByStatus(ReservationStatus.CANCELLED),
                reservationRepository.countByStatus(ReservationStatus.COMPLETED),
                invoiceRepository.countByStatus(InvoiceStatus.PENDING),
                invoiceRepository.countByStatus(InvoiceStatus.PAID),
                invoiceRepository.countByStatus(InvoiceStatus.CANCELLED),
                paymentRepository.count(),
                totalRevenue
        );
    }
}
