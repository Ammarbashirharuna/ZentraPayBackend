package com.zentrapay.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zentrapay.dto.apikey.ApiKeyResponse;
import com.zentrapay.dto.apikey.CreateApiKeyRequest;
import com.zentrapay.dto.apikey.CreateApiKeyResponse;
import com.zentrapay.entity.ApiKey;
import com.zentrapay.entity.User;
import com.zentrapay.exception.ResourceNotFoundException;
import com.zentrapay.repository.ApiKeyRepository;
import com.zentrapay.repository.UserRepository;
import com.zentrapay.security.ApiKeyAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * API key management service.
 *
 * Keys are generated with a {@code zp_} prefix + 32 random bytes (64 hex chars).
 * Only the SHA-256 hash is stored. The raw key is returned once at creation
 * and never again.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyService {

    private static final String KEY_PREFIX = "zp_";
    private static final int KEY_RANDOM_BYTES = 32;
    private static final long MAX_KEYS_PER_USER = 10;

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    /** Generate a new API key and return the raw key (shown once). */
    @Transactional
    public CreateApiKeyResponse createApiKey(CreateApiKeyRequest request) {
        User user = getCurrentUser();

        long activeCount = apiKeyRepository.countByUserIdAndIsActiveTrue(user.getId());
        if (activeCount >= MAX_KEYS_PER_USER) {
            throw new IllegalStateException(
                    "Maximum of " + MAX_KEYS_PER_USER + " active API keys reached. Revoke an existing key first.");
        }

        byte[] random = new byte[KEY_RANDOM_BYTES];
        secureRandom.nextBytes(random);
        String rawKey = KEY_PREFIX + HexFormat.of().formatHex(random);
        String keyHash = ApiKeyAuthenticationFilter.sha256Hex(rawKey);
        String keyPrefix = rawKey.substring(0, Math.min(8, rawKey.length()));

        String permissionsJson;
        try {
            permissionsJson = objectMapper.writeValueAsString(
                    request.getPermissions() != null ? request.getPermissions() : List.of());
        } catch (Exception e) {
            permissionsJson = "[]";
        }

        ApiKey apiKey = ApiKey.builder()
                .userId(user.getId())
                .name(request.getName())
                .keyHash(keyHash)
                .keyPrefix(keyPrefix)
                .permissions(permissionsJson)
                .isActive(true)
                .build();
        ApiKey saved = apiKeyRepository.save(apiKey);
        log.info("API key created: {} for user {}", saved.getId(), user.getId());

        return CreateApiKeyResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .rawKey(rawKey)
                .keyPrefix(keyPrefix)
                .permissions(request.getPermissions() != null ? request.getPermissions() : List.of())
                .build();
    }

    /** List the seller's API keys (without raw keys). */
    public Page<ApiKeyResponse> listMyApiKeys(Pageable pageable) {
        User user = getCurrentUser();
        return apiKeyRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(this::toResponse);
    }

    /** Revoke (deactivate) an API key. */
    @Transactional
    public void revokeApiKey(UUID keyId) {
        User user = getCurrentUser();
        ApiKey key = apiKeyRepository.findByIdAndUserId(keyId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("API key not found"));
        key.setIsActive(false);
        apiKeyRepository.save(key);
        log.info("API key {} revoked by user {}", keyId, user.getId());
    }

    private ApiKeyResponse toResponse(ApiKey key) {
        List<String> perms;
        try {
            perms = objectMapper.readValue(key.getPermissions(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            perms = List.of();
        }
        return ApiKeyResponse.builder()
                .id(key.getId())
                .name(key.getName())
                .keyPrefix(key.getKeyPrefix())
                .permissions(perms)
                .isActive(key.getIsActive())
                .lastUsedAt(key.getLastUsedAt())
                .createdAt(key.getCreatedAt())
                .build();
    }
}
