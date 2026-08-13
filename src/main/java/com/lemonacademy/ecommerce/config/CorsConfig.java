package com.lemonacademy.ecommerce.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("https://lemonhousecraft.in", "https://www.lemonhousecraft.in", "https://admin.lemonhousecraft.in", "https://www.admin.lemonhousecraft.in", "https://ecommercef-ten.vercel.app", "https://ecommerce-frontend-861245237403.asia-south1.run.app", "http://localhost:3000", "http://localhost:5173", "http://localhost:5174", "http://localhost:4200")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
