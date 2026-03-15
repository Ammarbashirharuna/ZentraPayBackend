package com.zentrapay.controller;

import com.zentrapay.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health Check", description = "System health and status endpoints")
public class HealthController {

    @GetMapping
    @Operation(
            summary = "Check system health",
            description = "Returns the current health status of the backend service"
    )
    public ApiResponse<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("service", "ZentraPay Backend");
        health.put("status", "UP");
        health.put("version", "1.0.0");

        return ApiResponse.success(health, "System is healthy");
    }
}