package com.zentrapay.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

/**
 * Utility class for JWT token operations
 *
 * JWT Structure: Header.Payload.Signature
 * - Header: Algorithm info (HS512)
 * - Payload: User data (userId, email, expiration)
 * - Signature: HMAC-SHA512 hash (proves authenticity)
 *
 * Security:
 * - Signed with secret key (only server knows)
 * - If tampered, signature won't match → rejected
 * - Expiration checked automatically
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration:86400000}")
    private Long expiration; // Default: 24 hours in milliseconds

    /**
     * Generate JWT token for authenticated user
     *
     * Token contains:
     * - subject (sub): User ID
     * - email: User email
     * - issuedAt (iat): Current timestamp
     * - expiration (exp): Current time + 24 hours
     *
     * Algorithm: HMAC-SHA512
     * - 512-bit cryptographic hash
     * - Extremely secure (impossible to forge without secret)
     *
     * @param userId User's UUID
     * @param email User's email
     * @return JWT token string
     */
    public String generateToken(UUID userId, String email) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        // Convert secret string to cryptographic key
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .setSubject(userId.toString())           // User ID
                .claim("email", email)                   // User email
                .setIssuedAt(now)                        // Issue time
                .setExpiration(expiryDate)               // Expiry time
                .signWith(key, SignatureAlgorithm.HS512) // Sign with secret
                .compact();                              // Build final token
    }

    /**
     * Get token expiration time as LocalDateTime
     *
     * @return Expiration time
     */
    public LocalDateTime getExpirationTime() {
        Date expiryDate = new Date(System.currentTimeMillis() + expiration);
        return expiryDate.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    /**
     * Extract user ID from token
     *
     * @param token JWT token
     * @return User UUID
     */
    public UUID getUserIdFromToken(String token) {
        Claims claims = getAllClaimsFromToken(token);
        return UUID.fromString(claims.getSubject());
    }

    /**
     * Extract email from token
     *
     * @param token JWT token
     * @return User email
     */
    public String getEmailFromToken(String token) {
        Claims claims = getAllClaimsFromToken(token);
        return claims.get("email", String.class);
    }

    /**
     * Validate JWT token
     *
     * Checks:
     * 1. Signature is valid (not tampered)
     * 2. Token not expired
     *
     * @param token JWT token
     * @return true if valid, false otherwise
     */
    public boolean validateToken(String token) {
        try {
            getAllClaimsFromToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extract all claims from token
     *
     * Process:
     * 1. Split token into parts (header.payload.signature)
     * 2. Decode header and payload (Base64)
     * 3. Verify signature using secret key
     * 4. If signature matches → token is authentic
     * 5. Check expiration
     *
     * @param token JWT token
     * @return Claims object with all token data
     */
    private Claims getAllClaimsFromToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        return Jwts.parser()
                .verifyWith(key)           // Verify signature
                .build()
                .parseSignedClaims(token)  // Parse and validate
                .getPayload();             // Get claims
    }
}