package com.zentrapay.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zentrapay.dto.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight in-memory rate limiter keyed by client IP.
 *
 * Two tiers:
 * <ul>
 *   <li><b>Public</b> endpoints ({@code /auth/**}) — strict limit to block
 *       credential stuffing and spam registration.</li>
 *   <li><b>General</b> endpoints — generous limit for normal API usage.</li>
 * </ul>
 *
 * This filter runs before the JWT filter (via {@code @Order}) so unauthenticated
 * requests are still rate-limited. In production, consider Redis-backed limiting
 * for multi-instance deployments.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final ConcurrentHashMap<String, RequestCounter> publicCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RequestCounter> generalCounts = new ConcurrentHashMap<>();

    @Value("${rate-limit.public-max:15}")
    private int publicMax;

    @Value("${rate-limit.public-window-seconds:60}")
    private long publicWindowSeconds;

    @Value("${rate-limit.general-max:100}")
    private int generalMax;

    @Value("${rate-limit.general-window-seconds:60}")
    private long generalWindowSeconds;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String ip = resolveClientIp(request);
        String path = request.getRequestURI();

        if (isPublicAuthPath(path)) {
            if (!tryAcquire(ip, publicCounts, publicMax, publicWindowSeconds)) {
                log.warn("Rate limit exceeded for {} on {}", ip, path);
                reject(response, "Too many requests. Please try again later.");
                return;
            }
        } else if (!isHealthOrSwaggerPath(path)) {
            if (!tryAcquire(ip, generalCounts, generalMax, generalWindowSeconds)) {
                log.warn("Rate limit exceeded for {} on {}", ip, path);
                reject(response, "Rate limit exceeded. Please slow down.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean tryAcquire(
            String key,
            ConcurrentHashMap<String, RequestCounter> store,
            int maxRequests,
            long windowSeconds
    ) {
        long now = System.currentTimeMillis();
        long windowStart = now - (windowSeconds * 1000);

        RequestCounter counter = store.compute(key, (k, existing) -> {
            if (existing == null || existing.windowStart < windowStart) {
                return new RequestCounter(now, 1);
            }
            existing.count++;
            return existing;
        });

        return counter != null && counter.count <= maxRequests;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isPublicAuthPath(String path) {
        return path.startsWith("/api/v1/auth/");
    }

    private boolean isHealthOrSwaggerPath(String path) {
        return path.startsWith("/actuator/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/api-docs")
                || path.equals("/api/v1/health");
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> body = ApiResponse.error(message, "Rate limit exceeded");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    /**
     * Simple counter within a time window. Immutable-ish: the caller
     * replaces the entry via {@code compute} when the window rolls over.
     */
    private static class RequestCounter {
        final long windowStart;
        int count;

        RequestCounter(long windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
