package com.caramel.support.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Restricts cross-origin access to the configured frontend origin only.
 * No wildcard origins and no credentials: the MVP has no cookies or auth headers.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final ChatProperties properties;

    public CorsConfig(ChatProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(properties.corsAllowedOrigin())
                .allowedMethods("POST")
                .allowedHeaders("Content-Type", "Accept")
                .allowCredentials(false);
    }
}
