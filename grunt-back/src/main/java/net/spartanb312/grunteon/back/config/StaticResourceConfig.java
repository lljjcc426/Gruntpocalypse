package net.spartanb312.grunteon.back.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration(proxyBeanMethods = false)
public class StaticResourceConfig implements WebFluxConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/css/**").addResourceLocations("classpath:/web/css/");
        registry.addResourceHandler("/js/**").addResourceLocations("classpath:/web/js/");
        registry.addResourceHandler("/fonts/**").addResourceLocations("classpath:/web/fonts/");
        registry.addResourceHandler("/schema/**").addResourceLocations("classpath:/web/schema/");
    }
}
