package com.zentrapay.controller;

import com.zentrapay.dto.ApiResponse;
import com.zentrapay.dto.apikey.ApiKeyResponse;
import com.zentrapay.dto.apikey.CreateApiKeyRequest;
import com.zentrapay.dto.apikey.CreateApiKeyResponse;
import com.zentrapay.service.ApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/api-keys")
@RequiredArgsConstructor
@Tag(name = "API Keys", description = "Manage API keys for programmatic access")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @PostMapping
    @Operation(summary = "Create API key",
            description = "Generate a new API key. The raw key is shown ONLY in this response.")
    public ResponseEntity<ApiResponse<CreateApiKeyResponse>> create(
            @Valid @RequestBody CreateApiKeyRequest request) {
        CreateApiKeyResponse response = apiKeyService.createApiKey(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "API key created. Save the key now — it will not be shown again."));
    }

    @GetMapping
    @Operation(summary = "List API keys",
            description = "List your API keys (without raw keys).")
    public ResponseEntity<ApiResponse<Page<ApiKeyResponse>>> list(
            @PageableDefault(size = 20) org.springframework.data.domain.Pageable pageable) {
        Page<ApiKeyResponse> page = apiKeyService.listMyApiKeys(pageable);
        return ResponseEntity.ok(ApiResponse.success(page, "API keys retrieved"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Revoke API key",
            description = "Deactivate an API key so it can no longer authenticate.")
    public ResponseEntity<ApiResponse<String>> revoke(@PathVariable UUID id) {
        apiKeyService.revokeApiKey(id);
        return ResponseEntity.ok(ApiResponse.success("API key revoked", "The key can no longer be used"));
    }
}
