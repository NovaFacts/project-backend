package com.novafacts.backend.config;

import com.novafacts.backend.auth.filter.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    // Injected from application.properties: cors.allowed-origins
    // Override at runtime via CORS_ALLOWED_ORIGINS environment variable.
    // Multiple origins are comma-separated: https://app.novafacts.com,https://admin.novafacts.com
    @Value("${cors.allowed-origins:http://localhost:5173}")
    private List<String> allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        // Usuarios: full endpoint restricted to ADMINISTRADOR (Sprint 1)
                        .requestMatchers("/api/usuarios/**").hasRole("ADMINISTRADOR")
                        // Reference-data and policy write operations restricted to ADMINISTRADOR
                        .requestMatchers(HttpMethod.POST,   "/api/propiedades/**", "/api/canales/**", "/api/temporadas/**", "/api/politicas/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.PUT,    "/api/propiedades/**", "/api/canales/**", "/api/temporadas/**", "/api/politicas/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/propiedades/**", "/api/canales/**", "/api/temporadas/**", "/api/politicas/**").hasRole("ADMINISTRADOR")
                        // Billing operations (factura, nota_credito, devolucion) — ADMINISTRADOR and CONTADOR only
                        .requestMatchers(HttpMethod.POST,   "/api/facturas/**", "/api/notas-credito/**", "/api/devoluciones/**").hasAnyRole("ADMINISTRADOR", "CONTADOR")
                        .requestMatchers(HttpMethod.PUT,    "/api/facturas/**", "/api/notas-credito/**", "/api/devoluciones/**").hasAnyRole("ADMINISTRADOR", "CONTADOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/facturas/**", "/api/notas-credito/**", "/api/devoluciones/**").hasAnyRole("ADMINISTRADOR", "CONTADOR")
                        // Financial operations (anticipo, penalidad) restricted to accounting roles
                        .requestMatchers(HttpMethod.POST,   "/api/anticipos/**", "/api/penalidades/**").hasAnyRole("ADMINISTRADOR", "CONTADOR", "AUXILIAR_CONTABLE")
                        .requestMatchers(HttpMethod.PUT,    "/api/anticipos/**", "/api/penalidades/**").hasAnyRole("ADMINISTRADOR", "CONTADOR", "AUXILIAR_CONTABLE")
                        .requestMatchers(HttpMethod.DELETE, "/api/anticipos/**", "/api/penalidades/**").hasAnyRole("ADMINISTRADOR", "CONTADOR", "AUXILIAR_CONTABLE")
                        // GET on financial data restricted by role (matches write-operation restrictions above)
                        .requestMatchers(HttpMethod.GET, "/api/anticipos/**", "/api/penalidades/**").hasAnyRole("ADMINISTRADOR", "CONTADOR", "AUXILIAR_CONTABLE")
                        .requestMatchers(HttpMethod.GET, "/api/facturas/**", "/api/notas-credito/**", "/api/devoluciones/**").hasAnyRole("ADMINISTRADOR", "CONTADOR")
                        // Role list is only needed by the user-creation form, which is admin-only
                        .requestMatchers(HttpMethod.GET, "/api/roles/**").hasRole("ADMINISTRADOR")
                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "Authorization"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
