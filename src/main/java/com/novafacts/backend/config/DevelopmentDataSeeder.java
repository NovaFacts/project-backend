package com.novafacts.backend.config;

import com.novafacts.backend.auth.entity.User;
import com.novafacts.backend.auth.repository.UserRepository;
import com.novafacts.backend.guest.entity.Guest;
import com.novafacts.backend.guest.repository.GuestRepository;
import com.novafacts.backend.property.entity.Property;
import com.novafacts.backend.property.repository.PropertyRepository;
import com.novafacts.backend.rol.entity.Rol;
import com.novafacts.backend.rol.repository.RolRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Disabled — set to profile "never" because the Property entity no longer has
 * city/capacity/pricePerNight fields (removed in Sprint 2). All dev data must
 * be loaded via Flyway seed migrations from Sprint 3 onwards.
 */
@Component
@Profile("never")
public class DevelopmentDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevelopmentDataSeeder.class);

    private final UserRepository userRepository;
    private final RolRepository rolRepository;
    private final GuestRepository guestRepository;
    private final PropertyRepository propertyRepository;
    private final PasswordEncoder passwordEncoder;

    public DevelopmentDataSeeder(
            UserRepository userRepository,
            RolRepository rolRepository,
            GuestRepository guestRepository,
            PropertyRepository propertyRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.rolRepository = rolRepository;
        this.guestRepository = guestRepository;
        this.propertyRepository = propertyRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        log.info("DevelopmentDataSeeder is disabled (@Profile(\"never\")). Skipping.");
    }

    @SuppressWarnings("unused")
    private void seedUsers() {
        Rol rolAdmin = rolRepository.findById(1).orElseThrow();
        Rol rolContador = rolRepository.findById(2).orElseThrow();

        Object[][] rows = {
            {"admin",     "admin123",     "Administrador",   rolAdmin},
            {"maria",     "maria123",     "María García",    rolContador},
        };
        for (Object[] row : rows) {
            User u = new User();
            u.setUsername((String) row[0]);
            u.setPassword(passwordEncoder.encode((String) row[1]));
            u.setNombre((String) row[2]);
            u.setRol((Rol) row[3]);
            u.setActivo(true);
            userRepository.save(u);
        }
    }

    @SuppressWarnings("unused")
    private void seedGuests() {
        Guest g = new Guest();
        g.setFirstName("Juan");
        g.setLastName("Pérez");
        g.setDocumentType("CC");
        g.setDocumentNumber("1000000001");
        guestRepository.save(g);
    }

    @SuppressWarnings("unused")
    private void seedProperties() {
        String[][] rows = {
            {"Villa El Retiro",     "Cra 9 #15-30"},
            {"Casa Bosque",         "Calle 82 #12-15"},
        };
        for (String[] row : rows) {
            Property p = new Property();
            p.setName(row[0]);
            p.setAddress(row[1]);
            propertyRepository.save(p);
        }
    }

    private void logSummary(List<?> saved) {
        log.info("Seeded {} records", saved.size());
    }
}
