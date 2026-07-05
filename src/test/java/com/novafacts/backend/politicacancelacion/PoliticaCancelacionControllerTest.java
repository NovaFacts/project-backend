package com.novafacts.backend.politicacancelacion;

import com.novafacts.backend.anticipo.repository.AnticipoRepository;
import com.novafacts.backend.auth.entity.User;
import com.novafacts.backend.auth.repository.UserRepository;
import com.novafacts.backend.canal.entity.Canal;
import com.novafacts.backend.canal.repository.CanalRepository;
import com.novafacts.backend.devolucion.repository.DevolucionRepository;
import com.novafacts.backend.factura.repository.FacturaRepository;
import com.novafacts.backend.notacredito.repository.NotaCreditoRepository;
import com.novafacts.backend.penalidad.repository.PenalidadRepository;
import com.novafacts.backend.politicacancelacion.entity.PoliticaCancelacion;
import com.novafacts.backend.politicacancelacion.repository.PoliticaCancelacionRepository;
import com.novafacts.backend.property.entity.Property;
import com.novafacts.backend.property.repository.PropertyRepository;
import com.novafacts.backend.reservation.entity.Reservation;
import com.novafacts.backend.reservation.entity.ReservationStatus;
import com.novafacts.backend.reservation.repository.ReservationRepository;
import com.novafacts.backend.rol.entity.Rol;
import com.novafacts.backend.rol.repository.RolRepository;
import com.novafacts.backend.temporada.entity.Temporada;
import com.novafacts.backend.temporada.repository.TemporadaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "admin@test.com", roles = {"ADMINISTRADOR"})
class PoliticaCancelacionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PoliticaCancelacionRepository politicaRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private DevolucionRepository devolucionRepository;
    @Autowired private NotaCreditoRepository notaCreditoRepository;
    @Autowired private AnticipoRepository anticipoRepository;
    @Autowired private PenalidadRepository penalidadRepository;
    @Autowired private FacturaRepository facturaRepository;
    @Autowired private PropertyRepository propertyRepository;
    @Autowired private CanalRepository canalRepository;
    @Autowired private TemporadaRepository temporadaRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RolRepository rolRepository;

    private Property savedProperty;
    private PoliticaCancelacion savedPolitica;

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
        temporadaRepository.deleteAll();
        canalRepository.deleteAll();
        userRepository.deleteAll();
        rolRepository.deleteAll();

        Property property = new Property();
        property.setName("Villa Test");
        property.setAddress("Calle 1 # 2-3");
        savedProperty = propertyRepository.save(property);

        PoliticaCancelacion politica = new PoliticaCancelacion();
        politica.setPropiedad(savedProperty);
        politica.setNombre("Política Test");
        politica.setPorcentajeReembolso(new BigDecimal("50.00"));
        politica.setDiasAviso(3);
        savedPolitica = politicaRepository.save(politica);
    }

    @Test
    void delete_politica_without_reservations_returns_204() throws Exception {
        mockMvc.perform(delete("/api/politicas/" + savedPolitica.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_politica_with_existing_reservation_returns_409() throws Exception {
        Rol rol = new Rol();
        rol.setNombre("Administrador");
        Rol savedRol = rolRepository.save(rol);

        User user = new User();
        user.setUsername("admin@test.com");
        user.setPassword("$2a$10$irrelevant");
        user.setNombre("Admin Test");
        user.setRol(savedRol);
        User savedUser = userRepository.save(user);

        Canal canal = new Canal();
        canal.setNombre("Canal Test");
        canal.setTipo("Directo");
        Canal savedCanal = canalRepository.save(canal);

        Temporada temporada = new Temporada();
        temporada.setNombre("Temporada Test");
        temporada.setFechaInicio(LocalDate.of(2028, 1, 1));
        temporada.setFechaFin(LocalDate.of(2028, 12, 31));
        Temporada savedTemporada = temporadaRepository.save(temporada);

        Reservation reservation = new Reservation();
        reservation.setCanal(savedCanal);
        reservation.setTemporada(savedTemporada);
        reservation.setPoliticaCancelacion(savedPolitica);
        reservation.setUsuarioCreador(savedUser);
        reservation.setPropertyId(savedProperty.getId());
        reservation.setClienteNombre("Cliente Test");
        reservation.setMontoTotal(new BigDecimal("500000.00"));
        reservation.setCheckIn(LocalDate.of(2028, 3, 1));
        reservation.setCheckOut(LocalDate.of(2028, 3, 5));
        reservation.setGuestCount(2);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservationRepository.save(reservation);

        mockMvc.perform(delete("/api/politicas/" + savedPolitica.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(
                        "No se puede eliminar la política de cancelación porque existen reservas que la referencian."));
    }

    @Test
    void delete_politica_with_nonexistent_id_returns_404() throws Exception {
        mockMvc.perform(delete("/api/politicas/99999"))
                .andExpect(status().isNotFound());
    }

    // ── L-4: porcentaje_reembolso range validation ────────────────────────────────

    @Test
    void create_politica_with_negative_porcentaje_returns_400() throws Exception {
        mockMvc.perform(post("/api/politicas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(politicaBody("-5.00")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_politica_with_porcentaje_over_100_returns_400() throws Exception {
        mockMvc.perform(post("/api/politicas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(politicaBody("120.00")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void save_politica_with_out_of_range_porcentaje_via_repository_throws_data_integrity_violation() {
        // Bypasses PoliticaCancelacionRequest's @Valid entirely, simulating a write path
        // (e.g. seeder, manual SQL, future import) that skips DTO validation. Only the
        // V14 CHECK constraint stands between this and an invalid row in the database.
        PoliticaCancelacion politica = new PoliticaCancelacion();
        politica.setPropiedad(savedProperty);
        politica.setNombre("Política Fuera de Rango");
        politica.setPorcentajeReembolso(new BigDecimal("120.00"));
        politica.setDiasAviso(5);

        assertThrows(DataIntegrityViolationException.class, () -> politicaRepository.save(politica));
    }

    private String politicaBody(String porcentajeReembolso) {
        return """
                {
                    "propiedadId": %d,
                    "nombre": "Política Fuera de Rango",
                    "porcentajeReembolso": %s,
                    "diasAviso": 5
                }
                """.formatted(savedProperty.getId(), porcentajeReembolso);
    }
}
