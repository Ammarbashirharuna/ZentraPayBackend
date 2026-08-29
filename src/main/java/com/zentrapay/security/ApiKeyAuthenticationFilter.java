package com.zentrapay.security;

import com.zentrapay.entity.ApiKey;
import com.zentrapay.repository.ApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * Authenticates requests that carry an API key in the {@code X-API-Key} header.
 *
 * API keys are stored as SHA-256 hashes — we never persist the raw key.
 * On lookup we hash the presented key and compare. If the key is valid and
 * active, the request is treated as authenticated with a role of {@code API_KEY}
 * so downstream authorization can distinguish API-key callers from JWT callers.
 *
 * This filter runs <em>after</em> the JWT filter. If a JWT is present and valid,
 * it takes precedence; the API key is only checked when no JWT auth is set.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-API-Key";
    private static final String ROLE_API_KEY = "ROLE_API_KEY";

    private final ApiKeyRepository apiKeyRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // Only attempt API-key auth if no JWT auth is already set.
        if (SecurityContextHolder.getContext().getAuthentication() == null
                || !SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {

            String apiKey = request.getHeader(HEADER_NAME);
            if (apiKey != null && !apiKey.isBlank()) {
                String hash = sha256Hex(apiKey);
                apiKeyRepository.findByKeyHashAndIsActiveTrue(hash)
                        .ifPresent(key -> {
                            key.setLastUsedAt(LocalDateTime.now());
                            apiKeyRepository.save(key);

                            UsernamePasswordAuthenticationToken auth =
                                    new UsernamePasswordAuthenticationToken(
                                            key.getUserId().toString(),
                                            null,
                                            List.of(new SimpleGrantedAuthority(ROLE_API_KEY)));
                            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(auth);

                            log.debug("API key authenticated for user {}", key.getUserId());
                        });
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Hash a raw API key with SHA-256 for storage comparison.
     * Keys are stored as {@code zp_}<64-hex-chars>; we hash the full string.
     */
    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
