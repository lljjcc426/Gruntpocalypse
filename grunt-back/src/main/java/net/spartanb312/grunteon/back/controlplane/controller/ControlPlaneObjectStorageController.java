package net.spartanb312.grunteon.back.controlplane.controller;

import java.io.File;
import java.util.Map;
import net.spartanb312.grunteon.back.controlplane.artifact.ArtifactDownloadGrantService;
import net.spartanb312.grunteon.back.controlplane.artifact.ArtifactStore;
import net.spartanb312.grunteon.back.support.ApiSupport;
import net.spartanb312.grunteon.obfuscator.web.ObjectTicket;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

@RestController
public class ControlPlaneObjectStorageController {

    private final ArtifactStore artifactStore;
    private final ArtifactDownloadGrantService artifactDownloadGrantService;

    public ControlPlaneObjectStorageController(
        ArtifactStore artifactStore,
        ArtifactDownloadGrantService artifactDownloadGrantService
    ) {
        this.artifactStore = artifactStore;
        this.artifactDownloadGrantService = artifactDownloadGrantService;
    }

    @PostMapping("/api/control/artifacts/upload-url")
    public Map<String, Object> createUploadUrl(@RequestBody(required = false) Map<String, Object> request) {
        String fileName = request == null ? null : stringValue(request.get("fileName"));
        String kind = request == null ? null : stringValue(request.get("kind"));
        ObjectTicket ticket = artifactStore.createUploadTicket(fileName, kind);
        Map<String, Object> result = ApiSupport.ok();
        result.put("objectKey", ticket.getObjectKey());
        result.put("method", ticket.getMethod());
        result.put("uploadUrl", ticket.getUrl().replace("/api/v1/storage/", "/api/control/storage/"));
        result.put("expiresAt", ticket.getExpiresAt());
        return result;
    }

    @PutMapping(path = "/api/control/storage/{*path}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Mono<ResponseEntity<Void>> putObject(@PathVariable String path, @RequestBody Mono<byte[]> bytes) {
        String normalizedPath = normalizeObjectKey(path);
        return bytes.map(content -> {
            artifactStore.putObject(normalizedPath, content);
            return ResponseEntity.ok().build();
        });
    }

    @GetMapping("/api/control/artifacts/download/{grantId}")
    public ResponseEntity<Resource> downloadByGrant(@PathVariable String grantId) {
        ArtifactDownloadGrantService.GrantedArtifact grantedArtifact = artifactDownloadGrantService.consumeGrant(grantId);
        File file = grantedArtifact.getFile();
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=" + grantedArtifact.getDownloadName())
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(new FileSystemResource(file));
    }

    @GetMapping("/api/control/storage/{*path}")
    public ResponseEntity<Resource> getObject(@PathVariable String path) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Direct object download is disabled");
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private String normalizeObjectKey(String path) {
        if (path == null) {
            return "";
        }
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }
}
