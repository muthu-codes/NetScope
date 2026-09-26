package com.netscope.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Allows the React dev server (localhost:5173) to call the API. Read-only verbs + POST for scan control. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] origins;

    public WebConfig(@Value("${netscope.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String[] origins) {
        this.origins = origins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "OPTIONS");
    }
}
