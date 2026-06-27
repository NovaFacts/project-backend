package com.novafacts.backend.invoice.repository;

import com.novafacts.backend.invoice.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    boolean existsByReservationId(Long reservationId);

    Optional<Invoice> findByReservationId(Long reservationId);
}
