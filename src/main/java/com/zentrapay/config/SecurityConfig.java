package com.zentrapay.config;

import com.zentrapay.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security Configuration
 *
 * Handles:
 * - JWT authentication
 * - Authorization rules
 * - Public vs protected endpoints
 * - CSRF disabled (we use JWT, not cookies)
 * - Stateless sessions (no server-side session storage)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Public URLs - no JWT token required
     *
     * Anyone can access these without logging in:
     * - Auth endpoints (register, login, verify email)
     * - Public payment pages (payment link checkout)
     * - Webhook endpoints (verified by CashOnRails signature, not JWT)
     * - Swagger UI and documentation
     * - Health checks
     */
    private static final String[] PUBLIC_URLS = {
            // Auth endpoints
            "/api/v1/auth/**",
            // Public payment pages
            "/api/v1/pay/**",
            // Webhook endpoints (verified by CashOnRails signature, not JWT)
            "/api/v1/webhooks/**",
            // Swagger UI and documentation
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api-docs/**",
            "/api-docs.yaml",
            "/v3/api-docs/**",
            // Actuator health
            "/actuator/health",
            "/actuator/info"
    };

    /**
     * Main security filter chain
     *
     * Configures:
     * 1. JWT authentication filter
     * 2. CSRF disabled (JWT doesn't need it)
     * 3. Stateless sessions
     * 4. Authorization rules
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) throws Exception {
        http
                // Disable CSRF
                // Why? We use JWT tokens in Authorization header, not cookies
                // CSRF attacks work through cookies, not headers
                .csrf(AbstractHttpConfigurer::disable)

                // Stateless session management
                // Why? Each request contains JWT token
                // No server-side session storage needed
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_URLS).permitAll()  // Public URLs
                        .anyRequest().authenticated()               // Everything else needs JWT
                )

                // Add JWT filter
                // This filter runs on every request
                // It extracts the JWT token and validates it
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    /**
     * Password encoder
     *
     * BCryptPasswordEncoder with strength 12
     * Hashes passwords so we never store plain text
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}