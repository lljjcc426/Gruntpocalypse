package net.spartanb312.grunteon.back.controlplane.artifact;

import io.minio.DownloadObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.File;
import net.spartanb312.grunteon.back.config.BackStorageProperties;
import net.spartanb312.grunteon.back.config.ExternalDependencyProperties;
import net.spartanb312.grunteon.obfuscator.web.ObjectStorageBackend;
import net.spartanb312.grunteon.obfuscator.web.ObjectStorageLocation;

public class MinioObjectStorageBackend implements ObjectStorageBackend {

    private final MinioClient minioClient;
    private final ExternalDependencyProperties.Minio properties;
    private final File cacheDir;

    public MinioObjectStorageBackend(
        MinioClient minioClient,
        ExternalDependencyProperties dependencyProperties,
        BackStorageProperties storageProperties
    ) {
        this.minioClient = minioClient;
        this.properties = dependencyProperties.getMinio();
        this.cacheDir = new File(storageProperties.getObjectBaseDir()).getAbsoluteFile();
        this.cacheDir.mkdirs();
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            minioClient.statObject(
                StatObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .build()
            );
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    @Override
    public void putObject(String objectKey, byte[] bytes) {
        try {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .build()
            );
            File cacheFile = resolveCacheFile(objectKey);
            cacheFile.getParentFile().mkdirs();
            java.nio.file.Files.write(cacheFile.toPath(), bytes);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to upload object to MinIO", exception);
        }
    }

    @Override
    public File getObject(String objectKey) {
        File cacheFile = resolveCacheFile(objectKey);
        cacheFile.getParentFile().mkdirs();
        try {
            minioClient.downloadObject(
                DownloadObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .filename(cacheFile.getAbsolutePath())
                    .overwrite(true)
                    .build()
            );
            return cacheFile;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to download object from MinIO", exception);
        }
    }

    @Override
    public ObjectStorageLocation describe(String objectKey) {
        return new ObjectStorageLocation(
            "minio",
            properties.getBucket(),
            objectKey.replace('\\', '/')
        );
    }

    private File resolveCacheFile(String objectKey) {
        String normalized = objectKey.replace('\\', '/');
        return new File(cacheDir, normalized).getAbsoluteFile();
    }
}
