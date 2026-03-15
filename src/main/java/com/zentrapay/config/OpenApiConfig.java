package com.zentrapay.config;

// Delete ALL imports and paste these:
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI zentrapayOpenAPI() {
        Server devServer = new Server();
        devServer.setUrl("http://localhost:8080");
        devServer.setDescription("Development Server");


        Info info = new Info()
                .title("ZentraPay API Documentation")
                .version("1.0.0")
                .description("Payment link generation platform - RESTful API documentation");

        return new OpenAPI()
                .info(info)
                .servers(List.of(devServer));
    }
}