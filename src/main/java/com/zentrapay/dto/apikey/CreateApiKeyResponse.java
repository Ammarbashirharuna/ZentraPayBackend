package com.zentrapay.dto.apikey;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Returned ONLY when a new API key is created. Contains the raw key that
 * must be saved by the user immediately — it will never be shown again.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateApiKeyResponse {

    private UUID id;
    private String name;
    /** The raw API key — show this ONCE and never again. */
    private String rawKey;
    private String keyPrefix;
    private List<String> permissions;
}
