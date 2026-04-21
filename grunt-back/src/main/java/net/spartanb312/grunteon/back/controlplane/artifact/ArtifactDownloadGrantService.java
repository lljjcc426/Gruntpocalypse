package net.spartanb312.grunteon.back.controlplane.artifact;

import java.io.File;
import java.time.Instant;

public interface ArtifactDownloadGrantService {

    ArtifactDownloadGrant issueGrant(String objectKey, String downloadName);

    GrantedArtifact consumeGrant(String grantId);

    final class ArtifactDownloadGrant {

        private final String grantId;
        private final String method;
        private final String url;
        private final Instant expiresAt;

        public ArtifactDownloadGrant(String grantId, String method, String url, Instant expiresAt) {
            this.grantId = grantId;
            this.method = method;
            this.url = url;
            this.expiresAt = expiresAt;
        }

        public String getGrantId() {
            return grantId;
        }

        public String getMethod() {
            return method;
        }

        public String getUrl() {
            return url;
        }

        public Instant getExpiresAt() {
            return expiresAt;
        }
    }

    final class GrantedArtifact {

        private final File file;
        private final String downloadName;

        public GrantedArtifact(File file, String downloadName) {
            this.file = file;
            this.downloadName = downloadName;
        }

        public File getFile() {
            return file;
        }

        public String getDownloadName() {
            return downloadName;
        }
    }
}
