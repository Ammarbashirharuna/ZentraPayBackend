package com.zentrapay.security;

import com.zentrapay.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

/**
 * JWT Authentication Filter
 *
 * Runs on every HTTP request.
 *
 * Purpose:
 * 1. Extract JWT token from Authorization header
 * 2. Validate the token
 * 3. Set the authenticated user in SecurityContext
 * 4. Allow the request to proceed
 *
 * How it works:
 * Request comes in with header: Authorization: Bearer eyJhbGci...
 *              ↓
 * Filter extracts the token
 *              ↓
 * Filter validates token with JwtUtil
 *              ↓
 * If valid: Extract user email from token, set in SecurityContext
 * If invalid: Token is ignored, request treated as unauthenticated
 *              ↓
 * Request proceeds to controller
 * Controller checks @Secured or @PreAuthorize
 * If endpoint needs auth and SecurityContext is empty → 401 Unauthorized
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * JwtUtil provided by Spring
     * Contains methods to validate and extract info from JWT tokens
     */
    private final JwtUtil jwtUtil;

    /**
     * Filter method - runs on every request
     *
     * @param request HttpServletRequest (incoming request)
     * @param response HttpServletResponse (outgoing response)
     * @param filterChain FilterChain (other filters to run after this)
     * @throws ServletException if servlet error
     * @throws IOException if IO error
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            // STEP 1: Extract JWT token from Authorization header
            String authHeader = request.getHeader("Authorization");

            // Authorization header should look like: "Bearer eyJhbGci..."
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                // Remove "Bearer " prefix, keep just the token
                String token = authHeader.substring(7);

                log.debug("Processing JWT token from request");

                // STEP 2: Validate token and extract user email
                String userEmail = jwtUtil.extractUsername(token);

                // STEP 3: Check if token is valid
                if (userEmail != null && jwtUtil.isTokenValid(token, userEmail)) {
                    log.debug("JWT token valid for user: {}", userEmail);

                    // STEP 4: Create authentication object
                    // This tells Spring "this user is authenticated"
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userEmail,              // principal (the user)
                                    null,                   // credentials (null, we use JWT not password)
                                    new ArrayList<>()       // authorities (empty, we'll use roles later)
                            );

                    // Set request details (IP address, session ID, etc.)
                    authentication.setDetails(
                            new WebAuthenticationDetailsSource()
                                    .buildDetails(request)
                    );

                    // STEP 5: Put authentication in SecurityContext
                    // This makes Spring recognize the user as authenticated
                    // Controllers can now access SecurityContextHolder.getContext().getAuthentication()
                    SecurityContextHolder.getContext()
                            .setAuthentication(authentication);

                    log.debug("User authenticated: {}", userEmail);

                } else {
                    // Token is invalid or expired
                    log.warn("Invalid or expired JWT token");
                }
            } else {
                // No Authorization header present
                // User is not authenticated for this request
                log.debug("No JWT token in request");
            }

        } catch (Exception e) {
            // Any error during token extraction/validation
            // Log it but continue (endpoint will reject if auth is required)
            log.error("Error processing JWT token: {}", e.getMessage());
        }

        // STEP 6: Continue filter chain
        // This allows the request to reach the controller
        filterChain.doFilter(request, response);
    }
}