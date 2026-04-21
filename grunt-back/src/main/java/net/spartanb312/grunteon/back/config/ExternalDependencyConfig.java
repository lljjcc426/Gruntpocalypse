package net.spartanb312.grunteon.back.config;

import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ExternalDependencyConfig {

    @Bean
    @ConditionalOnProperty(prefix = "grunteon.back.integration", name = "minio-enabled", havingValue = "true")
    public MinioClient minioClient(ExternalDependencyProperties properties) {
        ExternalDependencyProperties.Minio minio = properties.getMinio();
        return MinioClient.builder()
            .endpoint(minio.getEndpoint())
            .credentials(minio.getAccessKey(), minio.getSecretKey())
            .region(minio.getRegion())
            .build();
    }
}
