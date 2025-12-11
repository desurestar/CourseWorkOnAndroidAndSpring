package ru.zagrebin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Value("${media.path}")
    private String mediaPath;

    @Value("${media.public-url-prefix:/media}")
    private String publicUrlPrefix;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String resourceHandler = publicUrlPrefix + "/**";
        // file: + mediaPath + "/" — важно, чтобы путь оканчивался '/'
        String resourceLocation = "file:" + (mediaPath.endsWith("/") ? mediaPath : mediaPath + "/");
        registry.addResourceHandler(resourceHandler)
                .addResourceLocations(resourceLocation)
                .setCachePeriod(3600);
    }
}