package com.novafacts.backend.payment.repository;

import com.novafacts.backend.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    boolean existsByInvoiceId(Long invoiceId);

    Optional<Payment> findByInvoiceId(Long invoiceId);
}
