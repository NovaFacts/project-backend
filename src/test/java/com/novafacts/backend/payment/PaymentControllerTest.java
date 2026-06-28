package com.novafacts.backend.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novafacts.backend.guest.entity.Guest;
import com.novafacts.backend.guest.repository.GuestRepository;
import com.novafacts.backend.invoice.entity.Invoice;
import com.novafacts.backend.invoice.entity.InvoiceStatus;
import com.novafacts.backend.invoice.repository.InvoiceRepository;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class PaymentControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private GuestRepository guestRepository;
    @Autowired private PropertyRepository propertyRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private ObjectMapper objectMapper;

    private Invoice savedInvoice;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        invoiceRepository.deleteAll();
        reservationRepository.deleteAll();
        propertyRepository.deleteAll();
        guestRepository.deleteAll();

        Guest guest = new Guest();
        guest.setFirstName("Lucia");
        guest.setLastName("Torres");
        guest.setDocumentType("CC");
        guest.setDocumentNumber("55667788");
        Guest savedGuest = guestRepository.save(guest);

        Property property = new Property();
        property.setName("Finca Pago Test");
        property.setAddress("Vereda El Nogal");
        property.setCity("Villa de Leyva");
        property.setCapacity(8);
        property.setPricePerNight(new BigDecimal("400000.00"));
        Property savedProperty = propertyRepository.save(property);

        Reservation reservation = new Reservation();
        reservation.setGuestId(savedGuest.getId());
        reservation.setPropertyId(savedProperty.getId());
        reservation.setCheckIn(LocalDate.of(2027, 4, 1));
        reservation.setCheckOut(LocalDate.of(2027, 4, 4)); // 3 nights × 400000 = 1200000
        reservation.setGuestCount(4);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        Reservation savedReservation = reservationRepository.save(reservation);

        // 1200000 subtotal, × 0.19 = 228000 tax, total = 1428000
        Invoice invoice = new Invoice();
        invoice.setReservationId(savedReservation.getId());
        invoice.setSubtotal(new BigDecimal("1200000.00"));
        invoice.setTax(new BigDecimal("228000.00"));
        invoice.setTotal(new BigDecimal("1428000.00"));
        invoice.setStatus(InvoiceStatus.PENDING);
        savedInvoice = invoiceRepository.save(invoice);
    }

    private String paymentBody(String method) {
        return """
                {"invoiceId": %d, "paymentMethod": "%s"}
                """.formatted(savedInvoice.getId(), method);
    }

    @Test
    void create_payment_returns_201_with_amount_from_invoice() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "invoiceId": %d,
                                    "paymentMethod": "TRANSFER",
                                    "reference": "REF-TEST-001"
                                }
                                """.formatted(savedInvoice.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoiceId").value(savedInvoice.getId()))
                .andExpect(jsonPath("$.amount").value(1428000.0))
                .andExpect(jsonPath("$.paymentMethod").value("TRANSFER"))
                .andExpect(jsonPath("$.reference").value("REF-TEST-001"))
                .andExpect(jsonPath("$.paidAt").exists());
    }

    @Test
    void duplicate_payment_returns_409() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("CASH")))
                .andExpect(status().isCreated());

        // Invoice is now PAID; second payment attempt fails
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("CARD")))
                .andExpect(status().isConflict());
    }

    @Test
    void invoice_becomes_paid_after_payment() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("TRANSFER")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/invoices/" + savedInvoice.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void get_payment_by_invoice_returns_200() throws Exception {
        String createResponse = mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "invoiceId": %d,
                                    "paymentMethod": "CARD",
                                    "reference": "REF-CARD-002"
                                }
                                """.formatted(savedInvoice.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long paymentId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(get("/api/payments/by-invoice/" + savedInvoice.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId))
                .andExpect(jsonPath("$.invoiceId").value(savedInvoice.getId()));
    }

    @Test
    void delete_confirmed_payment_returns_409() throws Exception {
        String createResponse = mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("TRANSFER")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long paymentId = objectMapper.readTree(createResponse).get("id").asLong();

        // Invoice is PAID — delete must be rejected
        mockMvc.perform(delete("/api/payments/" + paymentId))
                .andExpect(status().isConflict());
    }
}
