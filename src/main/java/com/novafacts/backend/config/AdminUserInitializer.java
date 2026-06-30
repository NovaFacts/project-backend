package com.novafacts.backend.config;

import com.novafacts.backend.auth.entity.User;
import com.novafacts.backend.auth.repository.UserRepository;
import com.novafacts.backend.rol.entity.Rol;
import com.novafacts.backend.rol.repository.RolRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures at least one Administrador user exists in the database on every startup.
 * Runs in ALL Spring profiles (dev, test, prod) after Flyway migrations complete.
 * Idempotent: if the configured admin email already exists, does nothing.
 *
 * Credentials are controlled via environment variables:
 *   ADMIN_EMAIL    — defaults to admin@novafacts.com
 *   ADMIN_PASSWORD — defaults to Admin2024! (MUST be changed in production)
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class AdminUserInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserInitializer.class);
    private static final int ROL_ADMINISTRADOR_ID = 1;
    private static final String DEFAULT_PASSWORD   = "Admin2024!";

    @Value("${admin.init.email:admin@novafacts.com}")
    private String adminEmail;

    @Value("${admin.init.password:Admin2024!}")
    private String adminPassword;

    private final UserRepository  userRepository;
    private final RolRepository   rolRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserInitializer(UserRepository userRepository,
                                RolRepository rolRepository,
                                PasswordEncoder passwordEncoder) {
        this.userRepository  = userRepository;
        this.rolRepository   = rolRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsername(adminEmail)) {
            log.debug("AdminUserInitializer: '{}' ya existe, sin cambios.", adminEmail);
            return;
        }

        Rol rolAdmin = rolRepository.findById(ROL_ADMINISTRADOR_ID).orElse(null);
        if (rolAdmin == null) {
            // Expected in test contexts (H2, no Flyway). Skipped safely.
            // In production this means Flyway V2 did not run — investigate before proceeding.
            log.warn("AdminUserInitializer: rol Administrador (id={}) no encontrado — "
                    + "omitiendo creación del usuario admin. "
                    + "En producción verifique que Flyway V2 ejecutó correctamente.",
                    ROL_ADMINISTRADOR_ID);
            return;
        }

        User admin = new User();
        admin.setUsername(adminEmail);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setNombre("Administrador Sistema");
        admin.setRol(rolAdmin);
        admin.setActivo(true);
        userRepository.save(admin);

        if (DEFAULT_PASSWORD.equals(adminPassword)) {
            log.warn("╔══════════════════════════════════════════════════════════════════╗");
            log.warn("║  ADVERTENCIA DE SEGURIDAD — CONTRASEÑA POR DEFECTO              ║");
            log.warn("║  Usuario: {}                                    ║", adminEmail);
            log.warn("║  Contraseña inicial: Admin2024!                                 ║");
            log.warn("║  Cambie la contraseña inmediatamente después del primer login.  ║");
            log.warn("║  En producción defina la variable de entorno ADMIN_PASSWORD.    ║");
            log.warn("╚══════════════════════════════════════════════════════════════════╝");
        } else {
            log.info("AdminUserInitializer: usuario admin '{}' creado correctamente.", adminEmail);
        }
    }
}
