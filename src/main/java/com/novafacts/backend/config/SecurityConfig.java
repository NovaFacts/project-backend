package com.novafacts.backend.config;

import com.novafacts.backend.auth.filter.JwtAuthenticationFilter;
import com.novafacts.backend.auth.filter.LoginRateLimitFilter;
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
    private final LoginRateLimitFilter loginRateLimitFilter;

    // Injected from application.properties: cors.allowed-origins
    // Override at runtime via CORS_ALLOWED_ORIGINS environment variable.
    // Multiple origins are comma-separated: https://app.novafacts.com,https://admin.novafacts.com
    @Value("${cors.allowed-origins:http://localhost:5173}")
    private List<String> allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          LoginRateLimitFilter loginRateLimitFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.loginRateLimitFilter = loginRateLimitFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        // Health must be reachable without a JWT — Docker/orchestrator healthchecks
                        // call this directly with no credentials. Only "health" is ever web-exposed
                        // (see application.properties), and show-details=never means this leaks
                        // nothing beyond UP/DOWN, so permitAll here is safe.
                        .requestMatchers("/actuator/health/**").permitAll()
                        // Defense-in-depth: if web exposure is ever widened to other actuator
                        // endpoints later, they don't silently inherit permitAll from the line
                        // above — anything else under /actuator/** requires ADMINISTRADOR.
                        .requestMatchers("/actuator/**").hasRole("ADMINISTRADOR")
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
                        // M-14: dashboard aggregates financial data (invoice and anticipo figures)
                        // at the same sensitivity as the anticipo/penalidad endpoints above, so it
                        // gets the same role set. Recepcionista's job is reservation management, not
                        // financial reporting, so it is deliberately excluded here.
                        .requestMatchers(HttpMethod.GET, "/api/dashboard/**").hasAnyRole("ADMINISTRADOR", "CONTADOR", "AUXILIAR_CONTABLE")
                        // Reservations remain open to every authenticated role: Recepcionista's core
                        // job is reservation management, and Auxiliar/Contador/Administrador all
                        // legitimately reference reservations for their own work. montoTotal here is
                        // an operational booking figure, not an accounting aggregate.
                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        // M-14 follow-up: without an explicit AccessDeniedHandler, Spring Security's
                        // ExceptionTranslationFilter falls back to the authenticationEntryPoint (401)
                        // for AccessDeniedException too, so an authenticated-but-unauthorized request
                        // was indistinguishable from an unauthenticated one. This applies to every
                        // hasRole/hasAnyRole rule above, not just the dashboard rule added for M-14.
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                response.setStatus(HttpStatus.FORBIDDEN.value()))
                )
                // Rate limiting runs first — an abusive request is rejected before JWT
                // parsing or any other processing touches it (fail fast). It only ever
                // acts on POST /api/auth/login; every other path passes straight through.
                .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // M-1 (AUDIT_v5): Spring splits a comma-separated cors.allowed-origins value into
        // a List<String> but does not trim whitespace, so "a.com, b.com" silently produces
        // a second origin (" b.com") that can never match a real Origin header. Trimming
        // here, immediately before the list reaches CorsConfiguration, fixes that without
        // touching ordering or duplicates.
        configuration.setAllowedOrigins(allowedOrigins.stream().map(String::strip).toList());
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
