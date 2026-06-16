package com.badmintonhub.escrow.config;

import com.badmintonhub.escrow.security.JwtAuthFilter;
import com.badmintonhub.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless security with defense-in-depth JWT re-validation. {@link JwtAuthFilter} re-validates the
 * forwarded {@code Authorization: Bearer} token via the shared {@link JwtUtil}; {@code @PreAuthorize}
 * on controllers enforces authorization.
 *
 * <p>escrow endpoints are STAFF/ADMIN-only operational queues — there is no public browse. Only
 * actuator health/info is public; everything else requires an authenticated principal.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    // JwtAuthFilter is injected as a method parameter (not the class constructor) so SecurityConfig has
    // no construction-time dependency — otherwise the JwtUtil @Bean below + JwtAuthFilter form a cycle.
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public JwtUtil jwtUtil(@Value("${jwt.secret}") String secret) {
        return new JwtUtil(secret);
    }
}
