package net.spartanb312.grunteon.back.controlplane.artifact;

import java.io.File;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.spartanb312.grunteon.back.config.ControlPlaneIntegrationProperties;
import net.spartanb312.grunteon.back.support.ApiSupport;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LocalArtifactDownloadGrantService implements ArtifactDownloadGrantService {

    private final ArtifactStore artifactStore;
    private final ControlPlaneIntegrationProperties integrationProperties;
    private final Map<String, DownloadGrantRecord> grants = new ConcurrentHashMap<>();

    public LocalArtifactDownloadGrantService(
        ArtifactStore artifactStore,
        ControlPlaneIntegrationProperties integrationProperties
    ) {
        this.artifactStore = artifactStore;
        this.integrationProperties = integrationProperties;
    }

    @Override
    public ArtifactDownloadGrant issueGrant(String objectKey, String downloadName) {
        cleanupExpired();
        File file = artifactStore.getObject(objectKey);
        String grantId = UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plusSeconds(integrationProperties.getDownloadGrantTtlSeconds());
        String safeName = ApiSupport.sanitizeFileName(downloadName == null ? file.getName() : downloadName, file.getName());
        grants.put(grantId, new DownloadGrantRecord(objectKey, safeName, expiresAt));
        return new ArtifactDownloadGrant(
            grantId,
            "GET",
            "/api/control/artifacts/download/" + grantId,
            expiresAt
        );
    }

    @Override
    public GrantedArtifact consumeGrant(String grantId) {
        cleanupExpired();
        DownloadGrantRecord record = grants.remove(grantId);
        if (record == null) {
            throw new ResponseStatusException(HttpStatus.GONE, "Artifact grant is invalid or expired");
        }
        if (record.expiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Artifact grant is invalid or expired");
        }
        File file = artifactStore.getObject(record.objectKey());
        return new GrantedArtifact(file, record.downloadName());
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        grants.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private record DownloadGrantRecord(
        String objectKey,
        String downloadName,
        Instant expiresAt
    ) {
    }
}
