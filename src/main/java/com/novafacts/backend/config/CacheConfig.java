package com.novafacts.backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * I-1: backs UserDetailsServiceImpl.loadUserByUsername() with a short-TTL,
 * in-process Caffeine cache so JwtAuthenticationFilter stops forcing a DB
 * round-trip on every authenticated request. TTL is intentionally short
 * (app.security.user-cache-ttl-seconds) so account deactivation still takes
 * effect within a bounded window rather than up to the JWT's full expiration.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String USER_DETAILS_CACHE = "userDetailsCache";

    @Bean
    public CacheManager cacheManager(@Value("${app.security.user-cache-ttl-seconds}") long ttlSeconds) {
        CaffeineCacheManager manager = new CaffeineCacheManager(USER_DETAILS_CACHE);
        manager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(ttlSeconds, TimeUnit.SECONDS));
        return manager;
    }
}
