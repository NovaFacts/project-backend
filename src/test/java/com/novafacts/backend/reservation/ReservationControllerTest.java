package com.novafacts.backend.reservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novafacts.backend.guest.entity.Guest;
import com.novafacts.backend.guest.repository.GuestRepository;
import com.novafacts.backend.property.entity.Property;
import com.novafacts.backend.property.repository.PropertyRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class ReservationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private GuestRepository guestRepository;
    @Autowired private PropertyRepository propertyRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ObjectMapper objectMapper;

    private Guest savedGuest;
    private Property savedProperty;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        propertyRepository.deleteAll();
        guestRepository.deleteAll();

        Guest guest = new Guest();
        guest.setFirstName("María");
        guest.setLastName("García");
        guest.setDocumentType("CC");
        guest.setDocumentNumber("87654321");
        savedGuest = guestRepository.save(guest);

        Property property = new Property();
        property.setName("Villa Reserva Test");
        property.setAddress("Avenida 10 # 50-60");
        savedProperty = propertyRepository.save(property);
    }

    private String reservationBody(String checkIn, String checkOut) {
        return """
                {
                    "guestId": %d,
                    "propertyId": %d,
                    "checkIn": "%s",
                    "checkOut": "%s",
                    "guestCount": 2
                }
                """.formatted(savedGuest.getId(), savedProperty.getId(), checkIn, checkOut);
    }

    @Test
    void create_reservation_returns_201() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody("2027-06-01", "2027-06-06")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.guestId").value(savedGuest.getId()))
                .andExpect(jsonPath("$.propertyId").value(savedProperty.getId()));
    }

    @Test
    void overlapping_reservation_returns_409() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody("2027-07-01", "2027-07-08")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody("2027-07-04", "2027-07-11")))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelled_reservation_does_not_block_dates() throws Exception {
        String createResponse = mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody("2027-08-10", "2027-08-15")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long reservationId = objectMapper.readTree(createResponse).get("id").asLong();

        String cancelBody = """
                {
                    "guestId": %d,
                    "propertyId": %d,
                    "checkIn": "2027-08-10",
                    "checkOut": "2027-08-15",
                    "guestCount": 2,
                    "status": "CANCELLED"
                }
                """.formatted(savedGuest.getId(), savedProperty.getId());

        mockMvc.perform(put("/api/reservations/" + reservationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody("2027-08-10", "2027-08-15")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void update_reservation_returns_200() throws Exception {
        String createResponse = mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody("2027-09-01", "2027-09-05")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long reservationId = objectMapper.readTree(createResponse).get("id").asLong();

        String updateBody = """
                {
                    "guestId": %d,
                    "propertyId": %d,
                    "checkIn": "2027-09-10",
                    "checkOut": "2027-09-15",
                    "guestCount": 4,
                    "status": "CONFIRMED"
                }
                """.formatted(savedGuest.getId(), savedProperty.getId());

        mockMvc.perform(put("/api/reservations/" + reservationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestCount").value(4))
                .andExpect(jsonPath("$.checkIn").value("2027-09-10"))
                .andExpect(jsonPath("$.checkOut").value("2027-09-15"));
    }

    @Test
    void delete_reservation_returns_204_and_subsequent_get_returns_404() throws Exception {
        String createResponse = mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody("2027-10-01", "2027-10-05")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long reservationId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(delete("/api/reservations/" + reservationId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/reservations/" + reservationId))
                .andExpect(status().isNotFound());
    }
}
