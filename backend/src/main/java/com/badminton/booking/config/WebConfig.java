package com.badminton.booking.config;

import jakarta.servlet.MultipartConfigElement;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Bean
  public MultipartConfigElement multipartConfigElement() {
    return new MultipartConfigElement(
      "",
      5L * 1024 * 1024,
      6L * 1024 * 1024,
      0
    );
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    Path productUploadDirectory = Paths.get("uploads", "products")
      .toAbsolutePath()
      .normalize();

    registry
      .addResourceHandler("/uploads/products/**")
      .addResourceLocations(productUploadDirectory.toUri().toString());
    Path avatarDirectory = Paths.get("uploads", "avatars")
      .toAbsolutePath()
      .normalize();
    String avatarLocation = avatarDirectory.toUri().toString();
    registry
      .addResourceHandler("/uploads/avatars/**")
      .addResourceLocations(
        avatarLocation.endsWith("/") ? avatarLocation : avatarLocation + "/"
      );
  }
}
