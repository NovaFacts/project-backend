package com.novafacts.backend.property;

import com.novafacts.backend.anticipo.repository.AnticipoRepository;
import com.novafacts.backend.devolucion.repository.DevolucionRepository;
import com.novafacts.backend.factura.repository.FacturaRepository;
import com.novafacts.backend.notacredito.repository.NotaCreditoRepository;
import com.novafacts.backend.penalidad.repository.PenalidadRepository;
import com.novafacts.backend.politicacancelacion.repository.PoliticaCancelacionRepository;
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
@WithMockUser(roles = {"ADMINISTRADOR"})
class PropertyControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DevolucionRepository devolucionRepository;
    @Autowired private NotaCreditoRepository notaCreditoRepository;
    @Autowired private AnticipoRepository anticipoRepository;
    @Autowired private PenalidadRepository penalidadRepository;
    @Autowired private FacturaRepository facturaRepository;
    @Autowired private PropertyRepository propertyRepository;
    @Autowired private PoliticaCancelacionRepository politicaRepository;
    @Autowired private ReservationRepository reservationRepository;

    private Property savedProperty;

    @BeforeEach
    void setUp() {
        devolucionRepository.deleteAll();
        notaCreditoRepository.deleteAll();
        anticipoRepository.deleteAll();
        penalidadRepository.deleteAll();
        facturaRepository.deleteAll();
        reservationRepository.deleteAll();
        politicaRepository.deleteAll();
        propertyRepository.deleteAll();

        Property property = new Property();
        property.setName("Propiedad Test");
        property.setAddress("Calle 1 # 2-3");
        property.setDescripcion("Descripción de prueba");
        savedProperty = propertyRepository.save(property);
    }

    @Test
    void get_all_returns_200_with_page_response() throws Exception {
        mockMvc.perform(get("/api/propiedades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].name").value("Propiedad Test"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void create_property_returns_201() throws Exception {
        mockMvc.perform(post("/api/propiedades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Apartamento Nuevo",
                                    "address": "Carrera 7 # 100-50",
                                    "descripcion": "Apartamento moderno en el norte"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Apartamento Nuevo"))
                .andExpect(jsonPath("$.activa").value(true))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    void duplicate_property_returns_409() throws Exception {
        mockMvc.perform(post("/api/propiedades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Propiedad Test",
                                    "address": "Otra Dirección"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void get_by_id_returns_200() throws Exception {
        mockMvc.perform(get("/api/propiedades/" + savedProperty.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedProperty.getId()))
                .andExpect(jsonPath("$.name").value("Propiedad Test"))
                .andExpect(jsonPath("$.activa").value(true));
    }

    @Test
    void update_returns_200() throws Exception {
        mockMvc.perform(put("/api/propiedades/" + savedProperty.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Propiedad Test Actualizada",
                                    "address": "Nueva Dirección 456",
                                    "descripcion": "Nueva descripción",
                                    "activa": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Propiedad Test Actualizada"))
                .andExpect(jsonPath("$.address").value("Nueva Dirección 456"))
                .andExpect(jsonPath("$.activa").value(true));
    }

    @Test
    void soft_delete_returns_204_and_property_becomes_inactive() throws Exception {
        mockMvc.perform(delete("/api/propiedades/" + savedProperty.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/propiedades/" + savedProperty.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(false));
    }
}
