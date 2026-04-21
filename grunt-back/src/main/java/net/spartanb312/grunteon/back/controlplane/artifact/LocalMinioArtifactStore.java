package net.spartanb312.grunteon.back.controlplane.artifact;

import java.io.File;
import net.spartanb312.grunteon.obfuscator.web.ObjectStorageService;
import net.spartanb312.grunteon.obfuscator.web.ObjectTicket;
import org.springframework.stereotype.Service;

@Service
public class LocalMinioArtifactStore implements ArtifactStore {

    private final ObjectStorageService objectStorageService;

    public LocalMinioArtifactStore(ObjectStorageService objectStorageService) {
        this.objectStorageService = objectStorageService;
    }

    @Override
    public ObjectTicket createUploadTicket(String fileName) {
        return objectStorageService.createUploadTicket(fileName);
    }

    @Override
    public ObjectTicket createUploadTicket(String fileName, String kind) {
        return objectStorageService.createUploadTicket(fileName, kind);
    }

    @Override
    public String createManagedObjectKey(String fileName, String kind) {
        return objectStorageService.createManagedObjectKey(fileName, kind);
    }

    @Override
    public ObjectTicket createDownloadTicket(String objectKey) {
        return objectStorageService.createDownloadTicket(objectKey);
    }

    @Override
    public void putObject(String objectKey, byte[] bytes) {
        objectStorageService.putObject(objectKey, bytes);
    }

    @Override
    public File getObject(String objectKey) {
        return objectStorageService.getObject(objectKey);
    }
}
