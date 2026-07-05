package com.novafacts.backend.auth.service;

import com.novafacts.backend.auth.entity.User;
import com.novafacts.backend.auth.repository.UserRepository;
import com.novafacts.backend.config.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // I-1: short-TTL cache (see CacheConfig) so JwtAuthenticationFilter stops
    // forcing a DB lookup on every authenticated request. A negative result
    // (UsernameNotFoundException) is never cached — @Cacheable only caches
    // successful returns — so login failures are unaffected.
    @Override
    @Cacheable(CacheConfig.USER_DETAILS_CACHE)
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuario no encontrado: " + username));

        String authority = "ROLE_" + user.getRol().getNombre().toUpperCase().replace(" ", "_");

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(List.of(new SimpleGrantedAuthority(authority)))
                .disabled(!Boolean.TRUE.equals(user.getActivo()))
                .build();
    }
}
