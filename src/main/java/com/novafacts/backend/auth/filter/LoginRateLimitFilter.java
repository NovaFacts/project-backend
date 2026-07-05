package com.novafacts.backend.auth.filter;

import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brute-force protection for POST /api/auth/login only — every other request,
 * including already-authenticated API calls and Actuator health checks, passes
 * through untouched. One token bucket per client IP; Bucket4j's Bucket is
 * internally lock-free (CAS-based), and computeIfAbsent guarantees at most one
 * bucket is created per IP even if concurrent first-requests race.
 *
 * Memory-based and per-instance: buckets live in this JVM's heap only, so behind
 * multiple horizontally-scaled instances each one enforces its own independent
 * limit (an attacker spread across N instances effectively gets N times the
 * configured allowance). Acceptable for this project's current single-instance
 * deployment; a real multi-instance deployment would need a shared store
 * (e.g. Bucket4j's Redis-backed ProxyManager) instead — intentionally not
 * introduced here since this project has no Redis or other shared-state
 * dependency anywhere else, and adding one solely for this endpoint would be a
 * disproportionate new piece of infrastructure for a problem that does not
 * exist at today's deployment scale.
 */
@Slf4j
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/auth/login";

    @Value("${security.rate-limit.login.enabled:true}")
    private boolean enabled;

    @Value("${security.rate-limit.login.capacity:5}")
    private int capacity;

    @Value("${security.rate-limit.login.refill-tokens:5}")
    private int refillTokens;

    @Value("${security.rate-limit.login.refill-duration-seconds:60}")
    private long refillDurationSeconds;

    private final ConcurrentHashMap<String, Bucket> bucketsByIp = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!enabled || !isLoginRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = request.getRemoteAddr();
        Bucket bucket = bucketsByIp.computeIfAbsent(clientIp, ip -> newBucket());

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("Login rate limit exceeded for client {}", clientIp);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(refillDurationSeconds));
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"Demasiados intentos. Intente nuevamente más tarde.\"}");
    }

    private boolean isLoginRequest(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod()) && LOGIN_PATH.equals(request.getRequestURI());
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(capacity)
                        .refillGreedy(refillTokens, Duration.ofSeconds(refillDurationSeconds)))
                .build();
    }
}
