package net.spartanb312.grunteon.back.worker;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import kotlin.jvm.functions.Function0;
import net.spartanb312.grunteon.back.controlplane.artifact.ArtifactStore;
import net.spartanb312.grunteon.back.worker.protocol.WorkerProjectRequest;
import net.spartanb312.grunteon.back.worker.protocol.WorkerSessionRequest;
import net.spartanb312.grunteon.back.worker.protocol.WorkerSessionStateResponse;
import net.spartanb312.grunteon.obfuscator.web.ObfuscationService;
import net.spartanb312.grunteon.obfuscator.web.ObfuscationSession;
import net.spartanb312.grunteon.obfuscator.web.ProjectInspectionService;
import net.spartanb312.grunteon.obfuscator.web.ProjectMeta;
import net.spartanb312.grunteon.obfuscator.web.ProjectSource;
import net.spartanb312.grunteon.obfuscator.web.ProjectTree;
import net.spartanb312.grunteon.obfuscator.web.SessionAccessProfile;
import net.spartanb312.grunteon.obfuscator.web.SessionService;
import net.spartanb312.grunteon.obfuscator.web.StartResult;
import net.spartanb312.grunteon.obfuscator.web.WebBridgeSupport;
import org.springframework.stereotype.Service;

@Service
public class WorkerExecutionService {

    private final SessionService sessionService;
    private final ArtifactStore artifactStore;
    private final ProjectInspectionService projectInspectionService;
    private final ObfuscationService obfuscationService;

    public WorkerExecutionService(
        SessionService sessionService,
        ArtifactStore artifactStore,
        ProjectInspectionService projectInspectionService,
        ObfuscationService obfuscationService
    ) {
        this.sessionService = sessionService;
        this.artifactStore = artifactStore;
        this.projectInspectionService = projectInspectionService;
        this.obfuscationService = obfuscationService;
    }

    public StartResult startSession(
        WorkerSessionRequest request,
        Function0<kotlin.Unit> onFinish,
        Function0<kotlin.Unit> onStart
    ) {
        ObfuscationSession session = materializeSession(request, true);
        return obfuscationService.start(
            session,
            WebBridgeSupport.buildExecutionConfig(session),
            () -> {
                if (onStart != null) {
                    onStart.invoke();
                }
                return kotlin.Unit.INSTANCE;
            },
            () -> {
                externalizeOutput(session);
                if (onFinish != null) {
                    onFinish.invoke();
                }
                return kotlin.Unit.INSTANCE;
            }
        );
    }

    public ProjectMeta projectMeta(WorkerProjectRequest request) {
        ObfuscationSession session = materializeSession(request.session(), false);
        return projectInspectionService.projectMeta(session, parseScope(request.scope()));
    }

    public ProjectTree projectTree(WorkerProjectRequest request) {
        ObfuscationSession session = materializeSession(request.session(), false);
        return projectInspectionService.projectTree(session, parseScope(request.scope()));
    }

    public ProjectSource projectSource(WorkerProjectRequest request) {
        ObfuscationSession session = materializeSession(request.session(), false);
        return projectInspectionService.projectSource(session, parseScope(request.scope()), request.className());
    }

    public WorkerSessionStateResponse sessionState(WorkerSessionRequest request) {
        ObfuscationSession session = materializeSession(request, true);
        return new WorkerSessionStateResponse(
            session.getId(),
            session.getStatus().name(),
            session.getCurrentStep(),
            session.getProgress(),
            session.getTotalSteps(),
            session.getErrorMessage(),
            session.getOutputObjectKey(),
            java.util.List.copyOf(session.getConsoleLogs())
        );
    }

    private ObfuscationSession materializeSession(WorkerSessionRequest request, boolean includeOutput) {
        ObfuscationSession session = sessionService.getOrCreateSession(request.sessionId());
        session.configureControlPlane(
            SessionAccessProfile.parseOrNull(request.accessProfile()) == null
                ? SessionAccessProfile.SECURE
                : SessionAccessProfile.parseOrNull(request.accessProfile()),
            blankToDefault(request.controlPlane(), session.getControlPlane()),
            blankToDefault(request.workerPlane(), session.getWorkerPlane()),
            session.getOwnerUsername()
        );

        materializeConfig(session, request);
        materializeInput(session, request);
        materializeLibraries(session, request.libraryObjectRefs());
        materializeAssets(session, request.assetObjectRefs());
        if (includeOutput || session.getOutputJarPath() == null) {
            materializeOutput(session, request.outputObjectKey());
        }
        return session;
    }

    private void materializeConfig(ObfuscationSession session, WorkerSessionRequest request) {
        if (isBlank(request.configObjectKey())) return;
        if (!session.hasUploadedConfig()) {
            File configFile = artifactStore.getObject(request.configObjectKey());
            try {
                String jsonText = Files.readString(configFile.toPath(), StandardCharsets.UTF_8);
                sessionService.saveConfig(
                    session,
                    com.google.gson.JsonParser.parseString(jsonText).getAsJsonObject(),
                    blankToDefault(request.configDisplayName(), configFile.getName())
                );
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to restore config artifact", exception);
            }
        }
        sessionService.linkConfigArtifact(session, request.configObjectKey());
    }

    private void materializeInput(ObfuscationSession session, WorkerSessionRequest request) {
        if (isBlank(request.inputObjectKey())) return;
        if (!session.hasUploadedInput()) {
            File inputFile = artifactStore.getObject(request.inputObjectKey());
            sessionService.saveInput(session, inputFile);
        }
        sessionService.linkInputArtifact(session, request.inputObjectKey());
    }

    private void materializeLibraries(ObfuscationSession session, Map<String, String> refs) {
        if (refs == null || refs.isEmpty()) return;
        refs.forEach((fileName, objectKey) -> {
            File localFile = new File(session.getLibrariesDir(), fileName);
            if (!localFile.exists()) {
                File restored = artifactStore.getObject(objectKey);
                sessionService.saveLibraries(session, java.util.List.of(restored));
            }
            sessionService.linkLibraryArtifact(session, fileName, objectKey);
        });
    }

    private void materializeAssets(ObfuscationSession session, Map<String, String> refs) {
        if (refs == null || refs.isEmpty()) return;
        refs.forEach((fileName, objectKey) -> {
            File localFile = new File(session.getAssetsDir(), fileName);
            if (!localFile.exists()) {
                File restored = artifactStore.getObject(objectKey);
                sessionService.saveAssets(session, java.util.List.of(restored));
            }
            sessionService.linkAssetArtifact(session, fileName, objectKey);
        });
    }

    private void materializeOutput(ObfuscationSession session, String outputObjectKey) {
        if (isBlank(outputObjectKey)) return;
        if (!session.hasOutput()) {
            File outputFile = artifactStore.getObject(outputObjectKey);
            sessionService.saveOutput(session, outputFile);
        }
        sessionService.linkOutputArtifact(session, outputObjectKey);
    }

    private void externalizeOutput(ObfuscationSession session) {
        if (session.getStatus() != ObfuscationSession.Status.COMPLETED || session.getOutputJarPath() == null) {
            return;
        }
        File outputFile = new File(session.getOutputJarPath());
        if (!outputFile.exists()) {
            return;
        }
        String outputKey = isBlank(session.getOutputObjectKey())
            ? artifactStore.createManagedObjectKey(outputFile.getName(), "output")
            : session.getOutputObjectKey();
        try {
            artifactStore.putObject(outputKey, Files.readAllBytes(outputFile.toPath()));
            sessionService.linkOutputArtifact(session, outputKey);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to externalize worker output artifact", exception);
        }
    }

    private ObfuscationSession.ProjectScope parseScope(String scope) {
        ObfuscationSession.ProjectScope parsed = WebBridgeSupport.parseProjectScope(scope);
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid scope: " + scope);
        }
        return parsed;
    }

    private String blankToDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
