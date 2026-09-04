package com.zentrapay.config;

import com.zentrapay.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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
     * - Webhook endpoints (verified by Paystack signature, not JWT)
     * - Swagger UI and documentation
     * - Health checks
     */
    private static final String[] PUBLIC_URLS = {
            // Auth endpoints
            "/api/v1/auth/**",
            // Public payment pages
            "/api/v1/pay/**",
            // Webhook endpoints (verified by Paystack signature, not JWT)
            "/api/v1/webhooks/**",
            // Swagger UI and documentation
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api-docs/**",
            "/api-docs.yaml",
            "/v3/api-docs/**",
            // Actuator health
            "/actuator/health",
            "/actuator/info",
            // Rate limit info (public)
            "/api/v1/health"
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
    @Value("${app.cors-origins:http://localhost:3000,http://localhost:5173}")
    private String corsOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_URLS).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        for (String origin : corsOrigins.split(",")) {
            config.addAllowedOrigin(origin.trim());
        }
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
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