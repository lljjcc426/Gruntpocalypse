package net.spartanb312.grunteon.back;

import static org.assertj.core.api.Assertions.assertThat;

import io.minio.MinioClient;
import net.spartanb312.grunteon.back.controlplane.artifact.MinioObjectStorageBackend;
import net.spartanb312.grunteon.obfuscator.web.FilesystemObjectStorageBackend;
import net.spartanb312.grunteon.obfuscator.web.ObjectStorageBackend;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;

class ObjectStorageBackendSelectionTest {

    private final ReactiveWebApplicationContextRunner contextRunner =
        new ReactiveWebApplicationContextRunner()
            .withUserConfiguration(GruntBackApplication.class);

    @Test
    void fallsBackToFilesystemBackendWhenMinioIsDisabled() {
        contextRunner
            .withPropertyValues(
                "grunteon.back.integration.minio-enabled=false"
            )
            .run(context -> {
                assertThat(context).hasSingleBean(ObjectStorageBackend.class);
                assertThat(context).doesNotHaveBean(MinioClient.class);
                assertThat(context.getBean(ObjectStorageBackend.class))
                    .isInstanceOf(FilesystemObjectStorageBackend.class);
            });
    }

    @Test
    void usesMinioBackendWhenMinioIsEnabled() {
        contextRunner
            .withPropertyValues(
                "grunteon.back.integration.minio-enabled=true",
                "grunteon.back.infrastructure.fail-fast=false",
                "grunteon.back.infrastructure.minio.endpoint=http://127.0.0.1:9000",
                "grunteon.back.infrastructure.minio.access-key=test-access",
                "grunteon.back.infrastructure.minio.secret-key=test-secret",
                "grunteon.back.infrastructure.minio.bucket=test-bucket",
                "grunteon.back.infrastructure.minio.region=us-east-1"
            )
            .run(context -> {
                assertThat(context).hasSingleBean(MinioClient.class);
                assertThat(context).hasSingleBean(ObjectStorageBackend.class);
                assertThat(context.getBean(ObjectStorageBackend.class))
                    .isInstanceOf(MinioObjectStorageBackend.class);
            });
    }
}
