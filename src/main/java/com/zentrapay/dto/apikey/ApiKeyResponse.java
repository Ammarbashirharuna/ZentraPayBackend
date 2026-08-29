package com.zentrapay.dto.apikey;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * An API key as shown to its owner.
 *
 * The raw key is ONLY included in the {@code CreateApiKeyResponse} returned
 * at creation time. This response omits it for security.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyResponse {

    private UUID id;
    private String name;
    /** First 8 characters of the key, for identification (e.g. "zp_a1b2"). */
    private String keyPrefix;
    private List<String> permissions;
    private Boolean isActive;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;
}
