package com.novafacts.backend.dashboard;

import com.novafacts.backend.guest.entity.Guest;
import com.novafacts.backend.guest.repository.GuestRepository;
import com.novafacts.backend.invoice.entity.Invoice;
import com.novafacts.backend.invoice.entity.InvoiceStatus;
import com.novafacts.backend.invoice.repository.InvoiceRepository;
import com.novafacts.backend.payment.entity.Payment;
import com.novafacts.backend.payment.entity.PaymentMethod;
import com.novafacts.backend.payment.repository.PaymentRepository;
import com.novafacts.backend.property.entity.Property;
import com.novafacts.backend.property.repository.PropertyRepository;
import com.novafacts.backend.reservation.entity.Reservation;
import com.novafacts.backend.reservation.entity.ReservationStatus;
import com.novafacts.backend.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class DashboardControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private GuestRepository guestRepository;
    @Autowired private PropertyRepository propertyRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private PaymentRepository paymentRepository;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        invoiceRepository.deleteAll();
        reservationRepository.deleteAll();
        propertyRepository.deleteAll();
        guestRepository.deleteAll();
    }

    @Test
    void getDashboard_emptyDatabase_returnsAllZeros() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGuests").value(0))
                .andExpect(jsonPath("$.totalProperties").value(0))
                .andExpect(jsonPath("$.confirmedReservations").value(0))
                .andExpect(jsonPath("$.cancelledReservations").value(0))
                .andExpect(jsonPath("$.completedReservations").value(0))
                .andExpect(jsonPath("$.pendingInvoices").value(0))
                .andExpect(jsonPath("$.paidInvoices").value(0))
                .andExpect(jsonPath("$.cancelledInvoices").value(0))
                .andExpect(jsonPath("$.totalPayments").value(0))
                .andExpect(jsonPath("$.totalRevenue").value(0));
    }

    @Test
    void getDashboard_withSeedData_returnsCorrectAggregates() throws Exception {
        // 3 guests
        for (int i = 1; i <= 3; i++) {
            Guest g = new Guest();
            g.setFirstName("Nombre" + i);
            g.setLastName("Apellido" + i);
            g.setDocumentType("CC");
            g.setDocumentNumber("100000" + i);
            guestRepository.save(g);
        }

        // 2 properties
        for (int i = 1; i <= 2; i++) {
            Property p = new Property();
            p.setName("Propiedad " + i);
            p.setAddress("Calle " + i);
            p.setCity("Bogotá");
            p.setCapacity(4);
            p.setPricePerNight(new BigDecimal("200000.00"));
            propertyRepository.save(p);
        }

        Long guestId = guestRepository.findAll().get(0).getId();
        Long propertyId = propertyRepository.findAll().get(0).getId();

        // 4 reservations: 2 CONFIRMED, 1 CANCELLED, 1 COMPLETED
        saveReservation(guestId, propertyId, ReservationStatus.CONFIRMED,
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(12));
        saveReservation(guestId, propertyId, ReservationStatus.CONFIRMED,
                LocalDate.now().plusDays(20), LocalDate.now().plusDays(22));
        saveReservation(guestId, propertyId, ReservationStatus.CANCELLED,
                LocalDate.now().plusDays(30), LocalDate.now().plusDays(32));
        saveReservation(guestId, propertyId, ReservationStatus.COMPLETED,
                LocalDate.now().minusDays(5), LocalDate.now().minusDays(3));

        // 3 invoices (unique reservationId values): 1 PENDING, 1 PAID, 1 CANCELLED
        // No FK constraint enforced — using dummy distinct IDs
        Invoice pending = buildInvoice(1001L, InvoiceStatus.PENDING,
                "400000.00", "76000.00", "476000.00");
        invoiceRepository.save(pending);

        Invoice paid = buildInvoice(1002L, InvoiceStatus.PAID,
                "400000.00", "76000.00", "476000.00");
        invoiceRepository.save(paid);

        Invoice cancelled = buildInvoice(1003L, InvoiceStatus.CANCELLED,
                "400000.00", "76000.00", "476000.00");
        invoiceRepository.save(cancelled);

        // 1 payment linked to the PAID invoice
        Long paidInvoiceId = invoiceRepository.findAll().stream()
                .filter(i -> i.getStatus() == InvoiceStatus.PAID)
                .findFirst()
                .orElseThrow()
                .getId();

        Payment payment = new Payment();
        payment.setInvoiceId(paidInvoiceId);
        payment.setAmount(new BigDecimal("476000.00"));
        payment.setPaymentMethod(PaymentMethod.TRANSFER);
        payment.setPaidAt(LocalDateTime.now());
        paymentRepository.save(payment);

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGuests").value(3))
                .andExpect(jsonPath("$.totalProperties").value(2))
                .andExpect(jsonPath("$.confirmedReservations").value(2))
                .andExpect(jsonPath("$.cancelledReservations").value(1))
                .andExpect(jsonPath("$.completedReservations").value(1))
                .andExpect(jsonPath("$.pendingInvoices").value(1))
                .andExpect(jsonPath("$.paidInvoices").value(1))
                .andExpect(jsonPath("$.cancelledInvoices").value(1))
                .andExpect(jsonPath("$.totalPayments").value(1))
                .andExpect(jsonPath("$.totalRevenue").value(476000.00));
    }

    private void saveReservation(Long guestId, Long propertyId, ReservationStatus status,
                                 LocalDate checkIn, LocalDate checkOut) {
        Reservation r = new Reservation();
        r.setGuestId(guestId);
        r.setPropertyId(propertyId);
        r.setCheckIn(checkIn);
        r.setCheckOut(checkOut);
        r.setGuestCount(2);
        r.setStatus(status);
        reservationRepository.save(r);
    }

    private Invoice buildInvoice(Long reservationId, InvoiceStatus status,
                                 String subtotal, String tax, String total) {
        Invoice inv = new Invoice();
        inv.setReservationId(reservationId);
        inv.setSubtotal(new BigDecimal(subtotal));
        inv.setTax(new BigDecimal(tax));
        inv.setTotal(new BigDecimal(total));
        inv.setStatus(status);
        return inv;
    }
}
