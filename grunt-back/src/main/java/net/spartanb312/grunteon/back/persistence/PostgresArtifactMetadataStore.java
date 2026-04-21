package net.spartanb312.grunteon.back.persistence;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.spartanb312.grunteon.obfuscator.web.ArtifactMetadataQuery;
import net.spartanb312.grunteon.obfuscator.web.ArtifactMetadataStore;
import net.spartanb312.grunteon.obfuscator.web.PersistedArtifactBinding;
import net.spartanb312.grunteon.obfuscator.web.PersistedArtifactRecord;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.Parameter;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class PostgresArtifactMetadataStore implements ArtifactMetadataStore, ArtifactMetadataQuery {

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
                INSERT INTO control_artifact_ref (
                    object_key,
                    owner_type,
                    owner_id,
                    owner_role,
                    updated_at
                ) VALUES (
                    :objectKey,
                    :ownerType,
                    :ownerId,
                    :ownerRole,
                    CURRENT_TIMESTAMP
                )
                ON CONFLICT (object_key, owner_type, owner_id, owner_role)
                DO UPDATE SET updated_at = CURRENT_TIMESTAMP
                """
            )
            .bind("objectKey", objectKey)
            .bind("ownerType", ownerType)
            .bind("ownerId", ownerId)
            .bind("ownerRole", ownerRole)
            .fetch()
            .rowsUpdated()
            .onErrorResume(error -> Mono.empty())
            .subscribe();
    }

    @Override
    public PersistedArtifactRecord findArtifact(String objectKey) {
        try {
            PersistedArtifactRecord artifact = databaseClient.sql(
                    """
                    SELECT object_key, artifact_kind, file_name, storage_backend,
                           bucket_name, object_path, size_bytes, artifact_status,
                           created_at, updated_at
                    FROM control_artifact_manifest
                    WHERE object_key = :objectKey
                    """
                )
                .bind("objectKey", objectKey)
                .map((row, metadata) -> mapArtifactRow(
                    row.get("object_key", String.class),
                    row.get("artifact_kind", String.class),
                    row.get("file_name", String.class),
                    row.get("storage_backend", String.class),
                    row.get("bucket_name", String.class),
                    row.get("object_path", String.class),
                    row.get("size_bytes", Long.class),
                    row.get("artifact_status", String.class),
                    row.get("created_at"),
                    row.get("updated_at")
                ))
                .one()
                .blockOptional()
                .orElse(null);
            if (artifact == null) {
                return null;
            }
            return withBindings(artifact);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Override
    public List<PersistedArtifactRecord> loadArtifactsForOwner(String ownerType, String ownerId) {
        try {
            List<String> objectKeys = databaseClient.sql(
                    """
                    SELECT DISTINCT object_key
                    FROM control_artifact_ref
                    WHERE owner_type = :ownerType AND owner_id = :ownerId
                    ORDER BY object_key
                    """
                )
                .bind("ownerType", ownerType)
                .bind("ownerId", ownerId)
                .map((row, metadata) -> row.get("object_key", String.class))
                .all()
                .collectList()
                .blockOptional()
                .orElseGet(Collections::emptyList);
            List<PersistedArtifactRecord> result = new ArrayList<>();
            for (String objectKey : objectKeys) {
                try {
                    PersistedArtifactRecord artifact = findArtifact(objectKey);
                    if (artifact != null) {
                        result.add(artifact);
                    }
                } catch (RuntimeException ignored) {
                }
            }
            return result;
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    @Override
    public List<PersistedArtifactBinding> loadBindingsForObject(String objectKey) {
        try {
            return databaseClient.sql(
                    """
                    SELECT object_key, owner_type, owner_id, owner_role, created_at, updated_at
                    FROM control_artifact_ref
                    WHERE object_key = :objectKey
                    ORDER BY owner_type, owner_id, owner_role
                    """
                )
                .bind("objectKey", objectKey)
                .map((row, metadata) -> new PersistedArtifactBinding(
                    row.get("object_key", String.class),
                    row.get("owner_type", String.class),
                    row.get("owner_id", String.class),
                    row.get("owner_role", String.class),
                    toIsoString(row.get("created_at")),
                    toIsoString(row.get("updated_at"))
                ))
                .all()
                .collectList()
                .blockOptional()
                .orElseGet(Collections::emptyList);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private PersistedArtifactRecord withBindings(PersistedArtifactRecord artifact) {
        List<PersistedArtifactBinding> bindings = loadBindingsForObject(artifact.getObjectKey());
        return new PersistedArtifactRecord(
            artifact.getObjectKey(),
            artifact.getArtifactKind(),
            artifact.getFileName(),
            artifact.getStorageBackend(),
            artifact.getBucketName(),
            artifact.getObjectPath(),
            artifact.getSizeBytes(),
            artifact.getArtifactStatus(),
            artifact.getCreatedAt(),
            artifact.getUpdatedAt(),
            bindings
        );
    }

    private PersistedArtifactRecord mapArtifactRow(
        String objectKey,
        String artifactKind,
        String fileName,
        String storageBackend,
        String bucketName,
        String objectPath,
        Long sizeBytes,
        String artifactStatus,
        Object createdAt,
        Object updatedAt
    ) {
        return new PersistedArtifactRecord(
            objectKey,
            artifactKind,
            fileName,
            storageBackend,
            bucketName,
            objectPath,
            sizeBytes == null ? 0L : sizeBytes,
            artifactStatus,
            toIsoString(createdAt),
            toIsoString(updatedAt),
            List.of()
        );
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

    private String toIsoString(OffsetDateTime value) {
        return value == null ? null : value.toString();
    }

    private String toIsoString(Object value) {
        return value == null ? null : value.toString();
    }
}
