package com.zentrapay.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests that run against a real Postgres in Docker.
 *
 * The container is static so it is started once and shared across all
 * integration test classes (Testcontainers reuses the singleton within the JVM
 * and stops it via the Ryuk resource reaper at the end). Flyway runs our real
 * migrations against it, so these tests exercise the actual schema — including
 * constraints and jsonb columns — not an in-memory approximation.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("integration")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("zentrapay_test")
                    .withUsername("zentra")
                    .withPassword("zentra");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // Ensure Flyway builds the schema our @Entity mappings validate against.
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
