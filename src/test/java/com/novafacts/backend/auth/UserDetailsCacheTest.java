package com.novafacts.backend.auth;

import com.novafacts.backend.anticipo.repository.AnticipoRepository;
import com.novafacts.backend.auth.entity.User;
import com.novafacts.backend.auth.repository.UserRepository;
import com.novafacts.backend.canal.repository.CanalRepository;
import com.novafacts.backend.config.CacheConfig;
import com.novafacts.backend.devolucion.repository.DevolucionRepository;
import com.novafacts.backend.factura.repository.FacturaRepository;
import com.novafacts.backend.notacredito.repository.NotaCreditoRepository;
import com.novafacts.backend.penalidad.repository.PenalidadRepository;
import com.novafacts.backend.politicacancelacion.repository.PoliticaCancelacionRepository;
import com.novafacts.backend.property.repository.PropertyRepository;
import com.novafacts.backend.reservation.repository.ReservationRepository;
import com.novafacts.backend.rol.entity.Rol;
import com.novafacts.backend.rol.repository.RolRepository;
import com.novafacts.backend.temporada.repository.TemporadaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * I-1: verifies UserDetailsServiceImpl.loadUserByUsername() is backed by the
 * short-TTL Caffeine cache configured in CacheConfig, so JwtAuthenticationFilter
 * no longer forces a DB lookup on every authenticated request.
 * app.security.user-cache-ttl-seconds is overridden to 2s in application-test.properties
 * so expiration can be exercised without a slow real-world sleep.
 */
@SpringBootTest
@ActiveProfiles("test")
class UserDetailsCacheTest {

    private static final String USERNAME = "cache-test@novafacts.com";

    @Autowired private UserDetailsService userDetailsService;
    @Autowired private RolRepository rolRepository;
    @Autowired private CacheManager cacheManager;

    // Full FK-dependency cleanup cascade, matching the convention used by every
    // other @SpringBootTest class in this project: the Testcontainers database is
    // shared across test classes within a run, so leftover rows from another test
    // class's Reservation/Factura/etc. rows would otherwise block deleting `usuario`.
    @Autowired private DevolucionRepository devolucionRepository;
    @Autowired private NotaCreditoRepository notaCreditoRepository;
    @Autowired private AnticipoRepository anticipoRepository;
    @Autowired private PenalidadRepository penalidadRepository;
    @Autowired private FacturaRepository facturaRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private PoliticaCancelacionRepository politicaRepository;
    @Autowired private PropertyRepository propertyRepository;
    @Autowired private TemporadaRepository temporadaRepository;
    @Autowired private CanalRepository canalRepository;

    // Spies (rather than replaces) the real UserRepository bean — setup/save calls
    // below go through the same spy instance used for verification.
    @MockitoSpyBean private UserRepository userRepositorySpy;

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
        userRepositorySpy.deleteAll();
        rolRepository.deleteAll();

        // Cache instances persist across test methods within the same Spring context —
        // clear explicitly so each test starts from a guaranteed cache miss.
        cacheManager.getCache(CacheConfig.USER_DETAILS_CACHE).clear();

        Rol rol = new Rol();
        rol.setNombre("Administrador");
        Rol savedRol = rolRepository.save(rol);

        User user = new User();
        user.setUsername(USERNAME);
        user.setPassword("$2a$10$irrelevant");
        user.setNombre("Cache Test User");
        user.setRol(savedRol);
        user.setActivo(true);
        userRepositorySpy.save(user);
    }

    @Test
    void first_authentication_loads_from_repository() {
        UserDetails result = userDetailsService.loadUserByUsername(USERNAME);

        assertEquals(USERNAME, result.getUsername());
        verify(userRepositorySpy, times(1)).findByUsername(USERNAME);
    }

    @Test
    void repeated_authentications_within_ttl_reuse_cache() {
        userDetailsService.loadUserByUsername(USERNAME);
        userDetailsService.loadUserByUsername(USERNAME);
        userDetailsService.loadUserByUsername(USERNAME);

        // Three logical calls, but only the first should hit the repository —
        // the other two must be served from the cache within the 2s test TTL.
        verify(userRepositorySpy, times(1)).findByUsername(USERNAME);
    }

    @Test
    void cache_expires_after_ttl() throws InterruptedException {
        userDetailsService.loadUserByUsername(USERNAME);
        verify(userRepositorySpy, times(1)).findByUsername(USERNAME);

        // app.security.user-cache-ttl-seconds=2 in application-test.properties.
        Thread.sleep(2500);

        userDetailsService.loadUserByUsername(USERNAME);
        verify(userRepositorySpy, times(2)).findByUsername(USERNAME);
    }

    @Test
    void cache_is_keyed_per_username() {
        Rol rol = rolRepository.findByNombre("Administrador").orElseThrow();
        User otherUser = new User();
        otherUser.setUsername("other-cache-test@novafacts.com");
        otherUser.setPassword("$2a$10$irrelevant");
        otherUser.setNombre("Other Cache Test User");
        otherUser.setRol(rol);
        otherUser.setActivo(true);
        userRepositorySpy.save(otherUser);

        userDetailsService.loadUserByUsername(USERNAME);
        userDetailsService.loadUserByUsername("other-cache-test@novafacts.com");

        verify(userRepositorySpy, times(1)).findByUsername(USERNAME);
        verify(userRepositorySpy, times(1)).findByUsername("other-cache-test@novafacts.com");
    }
}
