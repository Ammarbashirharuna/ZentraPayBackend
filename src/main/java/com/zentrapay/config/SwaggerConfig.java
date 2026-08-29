package com.zentrapay.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI Configuration
 *
 * Configures Swagger UI to:
 * 1. Show API documentation
 * 2. Display Authorize button for JWT
 * 3. Include token in all requests
 *
 * Why separate config?
 * - Keeps security config focused on Spring Security
 * - Keeps API docs separate from security logic
 * - Easy to update documentation independently
 */
@Configuration
public class SwaggerConfig {

    /**
     * Configure OpenAPI with JWT security scheme
     *
     * This creates:
     * 1. API metadata (title, version, description)
     * 2. Security scheme (JWT Bearer token)
     * 3. Authorize button in Swagger UI
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                // API metadata
                .info(new Info()
                        .title("ZentraPay API Documentation")
                        .version("1.0.0")
                        .description(
                                "ZentraPay - Pan-African payment link platform\n\n" +
                                        "**Features:**\n" +
                                        "- User authentication with JWT\n" +
                                        "- Email verification via Resend\n" +
                                        "- Payout account management (bank, mobile money, EFT)\n" +
                                        "- Payment link generation with custom branding\n" +
                                        "- CashOnRails integration (HMAC + RSA signing)\n" +
                                        "- Earnings summary and analytics\n" +
                                        "- Referral program\n" +
                                        "- API key management\n\n" +
                                        "**Authentication:**\n" +
                                        "All endpoints (except /auth/**) require JWT Bearer token.\n" +
                                        "1. Register or login\n" +
                                        "2. Copy the JWT token from response\n" +
                                        "3. Click Authorize button\n" +
                                        "4. Paste token in the field\n" +
                                        "5. All requests will include your token"
                        )
                        .contact(new Contact()
                                .name("Appsware")
                                .url("https://appsware.ng")
                                .email("dev@appsware.ng")
                        )
                        .license(new License()
                                .name("Proprietary")
                                .url("https://appsware.ng")
                        )
                )

                // Security scheme definition
                // This tells Swagger how JWT authentication works
                .components(new Components()
                        .addSecuritySchemes("bearer-jwt",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)      // HTTP authentication
                                        .scheme("bearer")                     // Bearer tokens
                                        .bearerFormat("JWT")                  // JWT format
                                        .description(
                                                "Enter your JWT token here\n\n" +
                                                        "1. Login to get token\n" +
                                                        "2. Copy the 'token' value from response\n" +
                                                        "3. Paste it below (just the token, no 'Bearer' prefix)\n" +
                                                        "4. Click Authorize\n" +
                                                        "5. All requests will include your token automatically"
                                        )
                        )
                )

                // Apply security requirement globally
                // This makes the Authorize button appear
                .addSecurityItem(new SecurityRequirement()
                        .addList("bearer-jwt")
                );
    }
}