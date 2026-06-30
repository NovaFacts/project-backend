package com.novafacts.backend.auth.filter;

import com.novafacts.backend.auth.jwt.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);

        try {
            final String username = jwtService.extractUsername(token);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // CRITICAL-1: reject tokens belonging to deactivated accounts.
                // loadUserByUsername() sets disabled=true for activo=false users.
                // isTokenValid() does not check this flag, so we must do it explicitly.
                if (!userDetails.isEnabled()) {
                    log.warn("JWT auth rejected: account disabled for '{}'", username);
                    // Do not set authentication — Spring Security will enforce 401 downstream.
                } else if (jwtService.isTokenValid(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (ExpiredJwtException e) {
            // HIGH-5: differentiated, logged JWT rejection — expired tokens are expected
            // client behaviour, not an attack. DEBUG level to avoid alert noise.
            log.debug("JWT auth rejected: token expired on request to {}", request.getRequestURI());
            SecurityContextHolder.clearContext();
        } catch (SignatureException | MalformedJwtException e) {
            // Tampered or structurally broken token — WARN level, potential attack.
            log.warn("JWT auth rejected: invalid token on request to {} — {}",
                    request.getRequestURI(), e.getMessage());
            SecurityContextHolder.clearContext();
        } catch (JwtException e) {
            // Any other JJWT-level error (e.g. unsupported algorithm).
            log.warn("JWT auth rejected: {} on request to {}", e.getMessage(), request.getRequestURI());
            SecurityContextHolder.clearContext();
        } catch (Exception e) {
            // Unexpected error (programming bug, DB unavailable during loadUserByUsername, etc.).
            log.error("Unexpected error in JWT filter for {}", request.getRequestURI(), e);
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
