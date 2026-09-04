package com.zentrapay.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for integration tests that run against the Docker PostgreSQL
 * container that is already running locally.
 *
 * Uses @DynamicPropertySource to override the datasource properties so tests
 * use the test database instead of the dev database.
 */
@SpringBootTest
@ActiveProfiles("integration")
public abstract class AbstractIntegrationTest {

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        // Use the Docker PostgreSQL running on localhost:5432
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/zentrapay_test");
        registry.add("spring.datasource.username", () -> "coder");
        registry.add("spring.datasource.password", () -> "admin123");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
