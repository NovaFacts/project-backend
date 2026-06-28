package com.novafacts.backend.property;

import com.novafacts.backend.property.entity.Property;
import com.novafacts.backend.property.repository.PropertyRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class PropertyControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PropertyRepository propertyRepository;

    private Property savedProperty;

    @BeforeEach
    void setUp() {
        propertyRepository.deleteAll();

        Property property = new Property();
        property.setName("Propiedad Test");
        property.setAddress("Calle 1 # 2-3");
        property.setCity("Bogotá");
        property.setCapacity(4);
        property.setPricePerNight(new BigDecimal("150000.00"));
        savedProperty = propertyRepository.save(property);
    }

    @Test
    void create_property_returns_201() throws Exception {
        mockMvc.perform(post("/api/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Apartamento Nuevo",
                                    "address": "Carrera 7 # 100-50",
                                    "city": "Medellín",
                                    "capacity": 3,
                                    "pricePerNight": 200000.00
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Apartamento Nuevo"))
                .andExpect(jsonPath("$.capacity").value(3))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    void duplicate_property_returns_409() throws Exception {
        mockMvc.perform(post("/api/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Propiedad Test",
                                    "address": "Otra Dirección",
                                    "city": "Cali",
                                    "capacity": 2,
                                    "pricePerNight": 100000.00
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void get_by_id_returns_200() throws Exception {
        mockMvc.perform(get("/api/properties/" + savedProperty.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedProperty.getId()))
                .andExpect(jsonPath("$.name").value("Propiedad Test"))
                .andExpect(jsonPath("$.city").value("Bogotá"));
    }

    @Test
    void update_returns_200() throws Exception {
        mockMvc.perform(put("/api/properties/" + savedProperty.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Propiedad Test Actualizada",
                                    "address": "Nueva Dirección 456",
                                    "city": "Cartagena",
                                    "capacity": 6,
                                    "pricePerNight": 300000.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Propiedad Test Actualizada"))
                .andExpect(jsonPath("$.capacity").value(6))
                .andExpect(jsonPath("$.city").value("Cartagena"));
    }

    @Test
    void delete_returns_204_and_subsequent_get_returns_404() throws Exception {
        mockMvc.perform(delete("/api/properties/" + savedProperty.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/properties/" + savedProperty.getId()))
                .andExpect(status().isNotFound());
    }
}
