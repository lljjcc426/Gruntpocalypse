package net.spartanb312.grunteon.back.persistence;

import net.spartanb312.grunteon.obfuscator.web.ArtifactMetadataStore;
import org.springframework.r2dbc.core.Parameter;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class PostgresArtifactMetadataStore implements ArtifactMetadataStore {

    private final DatabaseClient databaseClient;

    public PostgresArtifactMetadataStore(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public void recordArtifact(
        String objectKey,
        String artifactKind,
        String fileName,
        String storageBackend,
        String bucketName,
        String objectPath,
        long sizeBytes,
        String status
    ) {
        updateArtifact(objectKey, artifactKind, fileName, storageBackend, bucketName, objectPath, sizeBytes, status)
            .flatMap(rows -> rows != null && rows > 0
                ? Mono.just(rows)
                : insertArtifact(objectKey, artifactKind, fileName, storageBackend, bucketName, objectPath, sizeBytes, status)
                    .onErrorResume(error -> updateArtifact(objectKey, artifactKind, fileName, storageBackend, bucketName, objectPath, sizeBytes, status))
            )
            .onErrorResume(error -> Mono.empty())
            .subscribe();
    }

    @Override
    public void bindArtifact(String objectKey, String ownerType, String ownerId, String ownerRole) {
        databaseClient.sql(
                """
                UPDATE control_artifact_manifest
                SET owner_type = :ownerType,
                    owner_id = :ownerId,
                    owner_role = :ownerRole,
                    updated_at = CURRENT_TIMESTAMP
                WHERE object_key = :objectKey
                """
            )
            .bind("ownerType", ownerType)
            .bind("ownerId", ownerId)
            .bind("ownerRole", ownerRole)
            .bind("objectKey", objectKey)
            .fetch()
            .rowsUpdated()
            .flatMap(rows -> rows != null && rows > 0
                ? Mono.just(rows)
                : insertOwnerPlaceholder(objectKey, ownerType, ownerId, ownerRole)
            )
            .onErrorResume(error -> Mono.empty())
            .subscribe();
    }

    private Mono<Long> updateArtifact(
        String objectKey,
        String artifactKind,
        String fileName,
        String storageBackend,
        String bucketName,
        String objectPath,
        long sizeBytes,
        String status
    ) {
        return databaseClient.sql(
                """
                UPDATE control_artifact_manifest
                SET artifact_kind = :artifactKind,
                    file_name = :fileName,
                    storage_backend = :storageBackend,
                    bucket_name = :bucketName,
                    object_path = :objectPath,
                    size_bytes = :sizeBytes,
                    artifact_status = :status,
                    updated_at = CURRENT_TIMESTAMP
                WHERE object_key = :objectKey
                """
            )
            .bind("artifactKind", artifactKind)
            .bind("fileName", fileName)
            .bind("storageBackend", storageBackend)
            .bind("bucketName", Parameter.fromOrEmpty(bucketName, String.class))
            .bind("objectPath", objectPath)
            .bind("sizeBytes", sizeBytes)
            .bind("status", status)
            .bind("objectKey", objectKey)
            .fetch()
            .rowsUpdated();
    }

    private Mono<Long> insertArtifact(
        String objectKey,
        String artifactKind,
        String fileName,
        String storageBackend,
        String bucketName,
        String objectPath,
        long sizeBytes,
        String status
    ) {
        return databaseClient.sql(
                """
                INSERT INTO control_artifact_manifest (
                    object_key,
                    artifact_kind,
                    file_name,
                    storage_backend,
                    bucket_name,
                    object_path,
                    size_bytes,
                    artifact_status
                ) VALUES (
                    :objectKey,
                    :artifactKind,
                    :fileName,
                    :storageBackend,
                    :bucketName,
                    :objectPath,
                    :sizeBytes,
                    :status
                )
                """
            )
            .bind("objectKey", objectKey)
            .bind("artifactKind", artifactKind)
            .bind("fileName", fileName)
            .bind("storageBackend", storageBackend)
            .bind("bucketName", Parameter.fromOrEmpty(bucketName, String.class))
            .bind("objectPath", objectPath)
            .bind("sizeBytes", sizeBytes)
            .bind("status", status)
            .fetch()
            .rowsUpdated();
    }

    private Mono<Long> insertOwnerPlaceholder(String objectKey, String ownerType, String ownerId, String ownerRole) {
        return databaseClient.sql(
                """
                INSERT INTO control_artifact_manifest (
                    object_key,
                    artifact_kind,
                    file_name,
                    owner_type,
                    owner_id,
                    owner_role,
                    storage_backend,
                    object_path,
                    size_bytes,
                    artifact_status
                ) VALUES (
                    :objectKey,
                    'asset',
                    :fileName,
                    :ownerType,
                    :ownerId,
                    :ownerRole,
                    'pending',
                    :objectPath,
                    0,
                    'PENDING'
                )
                """
            )
            .bind("objectKey", objectKey)
            .bind("fileName", objectKey.substring(objectKey.lastIndexOf('/') + 1))
            .bind("ownerType", ownerType)
            .bind("ownerId", ownerId)
            .bind("ownerRole", ownerRole)
            .bind("objectPath", objectKey)
            .fetch()
            .rowsUpdated()
            .onErrorResume(error -> Mono.empty());
    }

}
