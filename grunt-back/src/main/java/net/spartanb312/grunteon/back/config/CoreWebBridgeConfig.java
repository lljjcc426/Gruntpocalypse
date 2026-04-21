package net.spartanb312.grunteon.back.config;

import java.io.File;
import io.minio.MinioClient;
import net.spartanb312.grunteon.obfuscator.web.ArtifactMetadataStore;
import net.spartanb312.grunteon.back.controlplane.artifact.MinioObjectStorageBackend;
import net.spartanb312.grunteon.obfuscator.web.FilesystemObjectStorageBackend;
import net.spartanb312.grunteon.obfuscator.web.ObjectStorageBackend;
import net.spartanb312.grunteon.obfuscator.web.ObjectStorageService;
import net.spartanb312.grunteon.obfuscator.web.ObfuscationService;
import net.spartanb312.grunteon.obfuscator.web.PlatformTaskService;
import net.spartanb312.grunteon.obfuscator.web.ProjectInspectionService;
import net.spartanb312.grunteon.obfuscator.web.SessionMetadataStore;
import net.spartanb312.grunteon.obfuscator.web.SessionMetadataQuery;
import net.spartanb312.grunteon.obfuscator.web.SessionService;
import net.spartanb312.grunteon.obfuscator.web.TaskMetadataStore;
import net.spartanb312.grunteon.obfuscator.web.TaskMetadataQuery;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CoreWebBridgeConfig {

    @Bean
    public SessionService sessionService(
        BackStorageProperties properties,
        SessionMetadataStore sessionMetadataStore,
        ArtifactMetadataStore artifactMetadataStore,
        SessionMetadataQuery sessionMetadataQuery
    ) {
        return new SessionService(
            new File(properties.getSessionBaseDir()).getAbsoluteFile(),
            sessionMetadataStore,
            artifactMetadataStore,
            sessionMetadataQuery
        );
    }

    @Bean
    public ProjectInspectionService projectInspectionService() {
        return new ProjectInspectionService();
    }

    @Bean
    public ObfuscationService obfuscationService() {
        return new ObfuscationService();
    }

    @Bean
    @ConditionalOnProperty(prefix = "grunteon.back.integration", name = "minio-enabled", havingValue = "true")
    public ObjectStorageBackend minioObjectStorageBackend(
        MinioClient minioClient,
        ExternalDependencyProperties dependencyProperties,
        BackStorageProperties storageProperties
    ) {
        return new MinioObjectStorageBackend(minioClient, dependencyProperties, storageProperties);
    }

    @Bean
    @ConditionalOnMissingBean(ObjectStorageBackend.class)
    public ObjectStorageBackend filesystemObjectStorageBackend(BackStorageProperties properties) {
        return new FilesystemObjectStorageBackend(new File(properties.getObjectBaseDir()).getAbsoluteFile());
    }

    @Bean
    public ObjectStorageService objectStorageService(
        ObjectStorageBackend backend,
        ArtifactMetadataStore artifactMetadataStore
    ) {
        return new ObjectStorageService(backend, "/api/v1/storage", artifactMetadataStore);
    }

    @Bean
    public PlatformTaskService platformTaskService(
        SessionService sessionService,
        ObjectStorageService objectStorageService,
        ObfuscationService obfuscationService,
        TaskMetadataStore taskMetadataStore,
        ArtifactMetadataStore artifactMetadataStore,
        TaskMetadataQuery taskMetadataQuery
    ) {
        return new PlatformTaskService(
            sessionService,
            objectStorageService,
            obfuscationService,
            taskMetadataStore,
            artifactMetadataStore,
            taskMetadataQuery
        );
    }
}
