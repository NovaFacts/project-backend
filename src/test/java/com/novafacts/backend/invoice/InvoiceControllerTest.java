package com.novafacts.backend.invoice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novafacts.backend.guest.entity.Guest;
import com.novafacts.backend.guest.repository.GuestRepository;
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

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class InvoiceControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private GuestRepository guestRepository;
    @Autowired private PropertyRepository propertyRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private ObjectMapper objectMapper;

    private Reservation savedReservation;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        invoiceRepository.deleteAll();
        reservationRepository.deleteAll();
        propertyRepository.deleteAll();
        guestRepository.deleteAll();

        Guest guest = new Guest();
        guest.setFirstName("Carlos");
        guest.setLastName("López");
        guest.setDocumentType("CC");
        guest.setDocumentNumber("11223344");
        Guest savedGuest = guestRepository.save(guest);

        Property property = new Property();
        property.setName("Casa Factura Test");
        property.setAddress("Calle Factura 1");
        Property savedProperty = propertyRepository.save(property);

        Reservation reservation = new Reservation();
        reservation.setGuestId(savedGuest.getId());
        reservation.setPropertyId(savedProperty.getId());
        reservation.setCheckIn(LocalDate.of(2027, 2, 1));
        reservation.setCheckOut(LocalDate.of(2027, 2, 6)); // 5 nights
        reservation.setGuestCount(2);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        savedReservation = reservationRepository.save(reservation);
    }

    private String invoiceBody() {
        // subtotal provided by caller: 5 nights × 200 000 = 1 000 000 COP
        return "{\"reservationId\": %d, \"subtotal\": 1000000}".formatted(savedReservation.getId());
    }

    @Test
    void create_invoice_returns_201_with_calculated_amounts() throws Exception {
        // 1000000 subtotal × 0.19 = 190000 tax; total = 1190000
        mockMvc.perform(post("/api/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.subtotal").value(1000000.0))
                .andExpect(jsonPath("$.tax").value(190000.0))
                .andExpect(jsonPath("$.total").value(1190000.0))
                .andExpect(jsonPath("$.reservationId").value(savedReservation.getId()));
    }

    @Test
    void duplicate_invoice_returns_409() throws Exception {
        mockMvc.perform(post("/api/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceBody()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceBody()))
                .andExpect(status().isConflict());
    }

    @Test
    void invoice_becomes_paid_via_payment_creation() throws Exception {
        String createResponse = mockMvc.perform(post("/api/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long invoiceId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invoiceId\": %d, \"paymentMethod\": \"TRANSFER\"}".formatted(invoiceId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/invoices/" + invoiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.id").value(invoiceId));
    }

    @Test
    void cancel_invoice_returns_200_with_status_cancelled() throws Exception {
        String createResponse = mockMvc.perform(post("/api/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long invoiceId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(put("/api/invoices/" + invoiceId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void delete_paid_invoice_returns_409() throws Exception {
        String createResponse = mockMvc.perform(post("/api/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long invoiceId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invoiceId\": %d, \"paymentMethod\": \"CASH\"}".formatted(invoiceId)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/invoices/" + invoiceId))
                .andExpect(status().isConflict());
    }
}
