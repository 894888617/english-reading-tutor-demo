package com.demo.readingtutor.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {
    private final TtsProperties ttsProperties;

    public StaticResourceConfig(TtsProperties ttsProperties) {
        this.ttsProperties = ttsProperties;
    }

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.mediaType("mp3", MediaType.valueOf("audio/mpeg"));
        configurer.mediaType("wav", MediaType.valueOf("audio/wav"));
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(Path.of("data", "uploads").toUri().toString());
        registry.addResourceHandler("/covers/**")
                .addResourceLocations(Path.of("data", "covers").toUri().toString());
        registry.addResourceHandler("/audio-cache/**")
                .addResourceLocations(Paths.get(ttsProperties.getCacheDir()).toAbsolutePath().normalize().toUri().toString());
    }
}
