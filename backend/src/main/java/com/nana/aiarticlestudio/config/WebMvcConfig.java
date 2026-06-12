package com.nana.aiarticlestudio.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.image.local-dir:uploads/images}")
    private String imageLocalDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path imagePath = Paths.get(imageLocalDir).toAbsolutePath().normalize();

        registry.addResourceHandler("/uploads/images/**")
                .addResourceLocations(imagePath.toUri().toString() + "/");
    }
}