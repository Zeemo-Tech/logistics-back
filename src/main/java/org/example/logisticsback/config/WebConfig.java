package org.example.logisticsback.config;

import java.util.Arrays;
import org.example.logisticsback.auth.AuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final AuthInterceptor authInterceptor;
    private final String[] allowedOrigins;
    public WebConfig(AuthInterceptor authInterceptor, @Value("${app.cors.allowed-origins:http://localhost:8848}") String origins) {
        this.authInterceptor = authInterceptor;
        this.allowedOrigins = Arrays.stream(origins.split(",")).map(String::trim).filter(value -> !value.isBlank()).toArray(String[]::new);
    }
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/**")
                .excludePathPatterns("/error", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**");
    }
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**").allowedOrigins(allowedOrigins).allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS").allowedHeaders("*").exposedHeaders("token").allowCredentials(true).maxAge(3600);
    }
}
