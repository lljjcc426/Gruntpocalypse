package net.spartanb312.grunteon.back.controlplane.artifact;

import java.io.File;
import net.spartanb312.grunteon.obfuscator.web.ObjectTicket;

public interface ArtifactStore {

    ObjectTicket createUploadTicket(String fileName);
    default ObjectTicket createUploadTicket(String fileName, String kind) {
        return createUploadTicket(fileName);
    }
    default String createManagedObjectKey(String fileName, String kind) {
        return createUploadTicket(fileName, kind).getObjectKey();
    }

    ObjectTicket createDownloadTicket(String objectKey);

    void putObject(String objectKey, byte[] bytes);

    File getObject(String objectKey);
}
