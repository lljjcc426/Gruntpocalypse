package net.spartanb312.grunteon.back.worker.protocol;

import java.util.Map;
import net.spartanb312.grunteon.obfuscator.web.ObfuscationSession;

public record WorkerSessionRequest(
    String sessionId,
    String accessProfile,
    String controlPlane,
    String workerPlane,
    String configObjectKey,
    String configDisplayName,
    String inputObjectKey,
    String inputDisplayName,
    String outputObjectKey,
    Map<String, String> libraryObjectRefs,
    Map<String, String> assetObjectRefs
) {

    public static WorkerSessionRequest from(ObfuscationSession session) {
        return new WorkerSessionRequest(
            session.getId(),
            session.getAccessProfile().name(),
            session.getControlPlane(),
            session.getWorkerPlane(),
            session.getConfigObjectKey(),
            session.getConfigDisplayName(),
            session.getInputObjectKey(),
            session.getInputDisplayName(),
            session.getOutputObjectKey(),
            session.getLibraryObjectRefs(),
            session.getAssetObjectRefs()
        );
    }
}
