package net.spartanb312.grunteon.back.controlplane;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import net.spartanb312.grunteon.obfuscator.web.ArtifactMetadataQuery;
import net.spartanb312.grunteon.back.controlplane.artifact.ArtifactStore;
import net.spartanb312.grunteon.back.controlplane.cache.SessionPolicyCache;
import net.spartanb312.grunteon.back.controlplane.events.TaskEventPublisher;
import net.spartanb312.grunteon.back.controlplane.orchestration.TaskWorkflowOrchestrator;
import net.spartanb312.grunteon.back.controlplane.telemetry.ControlPlaneTelemetry;
import net.spartanb312.grunteon.back.config.BackPolicyProperties;
import net.spartanb312.grunteon.back.policy.ControlPlanePolicyService;
import net.spartanb312.grunteon.back.support.ApiSupport;
import net.spartanb312.grunteon.back.websocket.SessionSocketHub;
import net.spartanb312.grunteon.back.worker.WorkerGateway;
import net.spartanb312.grunteon.obfuscator.web.ObfuscationSession;
import net.spartanb312.grunteon.obfuscator.web.ProjectMeta;
import net.spartanb312.grunteon.obfuscator.web.ProjectSource;
import net.spartanb312.grunteon.obfuscator.web.ProjectTree;
import net.spartanb312.grunteon.obfuscator.web.PersistedSessionState;
import net.spartanb312.grunteon.obfuscator.web.SessionAccessProfile;
import net.spartanb312.grunteon.obfuscator.web.SessionMetadataQuery;
import net.spartanb312.grunteon.obfuscator.web.SessionService;
import net.spartanb312.grunteon.obfuscator.web.StartResult;
import net.spartanb312.grunteon.obfuscator.web.WebBridgeSupport;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ControlPlaneSessionFacade {

    private final SessionService sessionService;
    private final ArtifactStore artifactStore;
    private final WorkerGateway workerGateway;
    private final SessionSocketHub sessionSocketHub;
    private final ControlPlanePolicyService policyService;
    private final BackPolicyProperties policyProperties;
    private final SessionPolicyCache sessionPolicyCache;
    private final TaskEventPublisher taskEventPublisher;
    private final ControlPlaneTelemetry telemetry;
    private final TaskWorkflowOrchestrator workflowOrchestrator;
    private final SessionMetadataQuery sessionMetadataQuery;
    private final ArtifactMetadataQuery artifactMetadataQuery;

    public ControlPlaneSessionFacade(
        SessionService sessionService,
        ArtifactStore artifactStore,
        WorkerGateway workerGateway,
        SessionSocketHub sessionSocketHub,
        ControlPlanePolicyService policyService,
        BackPolicyProperties policyProperties,
        SessionPolicyCache sessionPolicyCache,
        TaskEventPublisher taskEventPublisher,
        ControlPlaneTelemetry telemetry,
        TaskWorkflowOrchestrator workflowOrchestrator,
        SessionMetadataQuery sessionMetadataQuery,
        ArtifactMetadataQuery artifactMetadataQuery
    ) {
        this.sessionService = sessionService;
        this.artifactStore = artifactStore;
        this.workerGateway = workerGateway;
        this.sessionSocketHub = sessionSocketHub;
        this.policyService = policyService;
        this.policyProperties = policyProperties;
        this.sessionPolicyCache = sessionPolicyCache;
        this.taskEventPublisher = taskEventPublisher;
        this.telemetry = telemetry;
        this.workflowOrchestrator = workflowOrchestrator;
        this.sessionMetadataQuery = sessionMetadataQuery;
        this.artifactMetadataQuery = artifactMetadataQuery;
    }

    public Map<String, Object> createSession(String requestedProfile, boolean uiClient, Authentication authentication) {
        SessionAccessProfile profile = policyService.resolveProfile(requestedProfile, uiClient);
        String ownerUsername = ApiSupport.requireUsername(authentication);
        ObfuscationSession session = sessionService.createSession(
            profile,
            policyProperties.getControlPlaneName(),
            policyProperties.getWorkerPlaneName(),
            ownerUsername
        );
        sessionPolicyCache.remember(session.getId(), profile);
        telemetry.record("control.session.create", Map.of("sessionId", session.getId(), "profile", profile.name()));
        taskEventPublisher.publish("control.session.create", Map.of("sessionId", session.getId(), "profile", profile.name()));
        Map<String, Object> result = ApiSupport.ok();
        result.put("sessionId", session.getId());
        result.put("session", policyService.filterSessionMap(session, ApiSupport.buildStatusResponse(session)));
        return result;
    }

    public Map<String, Object> status(String sessionId, Authentication authentication) {
        PersistedSessionState state = findSessionState(sessionId);
        if (state == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
        }
        requireSessionAccess(state, authentication);
        return policyService.filterSessionMap(state.getPolicyMode(), new LinkedHashMap<>(WebBridgeSupport.buildStatusMap(state)));
    }

    public List<String> logs(String sessionId, Authentication authentication) {
        ObfuscationSession session = sessionService.getSession(sessionId);
        if (session != null) {
            requireSessionAccess(session, authentication);
            return policyService.filterLogs(session);
        }
        PersistedSessionState state = findSessionState(sessionId);
        if (state == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
        }
        requireSessionAccess(state, authentication);
        return List.of();
    }

    public Map<String, Object> listSessions(Authentication authentication) {
        String ownerUsername = ApiSupport.requireUsername(authentication);
        boolean privileged = isPrivileged(authentication);
        Map<String, Object> result = ApiSupport.ok();
        result.put(
            "sessions",
            sessionMetadataQuery.loadSessions().stream()
                .filter(state -> privileged || ownerUsername.equals(state.getOwnerUsername()))
                .map(state -> policyService.filterSessionMap(state.getPolicyMode(), new LinkedHashMap<>(WebBridgeSupport.buildStatusMap(state))))
                .toList()
        );
        return result;
    }

    public Map<String, Object> artifacts(String sessionId, Authentication authentication) {
        PersistedSessionState state = findSessionState(sessionId);
        if (state == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
        }
        requireSessionAccess(state, authentication);
        Map<String, Object> result = ApiSupport.ok();
        result.put(
            "artifacts",
            artifactMetadataQuery.loadArtifactsForOwner("SESSION", sessionId).stream()
                .map(WebBridgeSupport::buildArtifactMap)
                .map(map -> policyService.filterArtifactMap(state.getPolicyMode(), new LinkedHashMap<>(map)))
                .toList()
        );
        return result;
    }

    public Map<String, Object> configUploaded(String sessionId, String fileName, com.google.gson.JsonObject json) {
        ObfuscationSession session = requireEditable(sessionId);
        sessionService.saveConfig(session, json, fileName);
        byte[] bytes = json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String objectKey = artifactStore.createManagedObjectKey(fileName, "config");
        artifactStore.putObject(objectKey, bytes);
        sessionService.linkConfigArtifact(session, objectKey);
        Map<String, Object> result = ApiSupport.ok();
        if (session.getAccessProfile().getAllowDetailedLogs()) {
            result.put("fileName", fileName);
            result.put("config", ApiSupport.gsonToJava(json));
        } else {
            result.put("accepted", true);
        }
        result.put("session", policyService.filterSessionMap(session, ApiSupport.buildStatusResponse(session)));
        return result;
    }

    public Map<String, Object> inputUploaded(String sessionId, File target) {
        ObfuscationSession session = requireEditable(sessionId);
        sessionService.saveInput(session, target);
        String objectKey = artifactStore.createManagedObjectKey(target.getName(), "input");
        sessionService.linkInputArtifact(session, storeLocalFile(objectKey, target));
        Map<String, Object> result = ApiSupport.ok();
        result.put("classCount", session.getInputClassList() == null ? 0 : session.getInputClassList().size());
        if (session.getAccessProfile().getAllowDetailedLogs()) {
            result.put("fileName", target.getName());
            result.put("classes", session.getInputClassList());
        }
        result.put("session", policyService.filterSessionMap(session, ApiSupport.buildStatusResponse(session)));
        return result;
    }

    public Map<String, Object> uploadedFiles(String sessionId, List<File> savedFiles, boolean libraries) {
        ObfuscationSession session = requireEditable(sessionId);
        if (libraries) {
            sessionService.saveLibraries(session, savedFiles);
        } else {
            sessionService.saveAssets(session, savedFiles);
        }
        for (File file : savedFiles) {
            String kind = libraries ? "asset" : "asset";
            String objectKey = artifactStore.createManagedObjectKey(file.getName(), kind);
            storeLocalFile(objectKey, file);
            if (libraries) {
                sessionService.linkLibraryArtifact(session, file.getName(), objectKey);
            } else {
                sessionService.linkAssetArtifact(session, file.getName(), objectKey);
            }
        }
        Map<String, Object> result = ApiSupport.ok();
        result.put("count", savedFiles.size());
        if (session.getAccessProfile().getAllowDetailedLogs()) {
            result.put("files", libraries ? session.getLibraryNames() : session.getAssetNames());
        }
        result.put("session", policyService.filterSessionMap(session, ApiSupport.buildStatusResponse(session)));
        return result;
    }

    public Map<String, Object> obfuscate(String sessionId, Authentication authentication) {
        ObfuscationSession session = requireSession(sessionId, authentication);
        rehydrateSessionArtifacts(session);
        sessionSocketHub.attachSessionCallbacks(session);
        telemetry.record("control.session.obfuscate", Map.of("sessionId", sessionId, "profile", session.getAccessProfile().name()));
        taskEventPublisher.publish("control.session.obfuscate", Map.of("sessionId", sessionId, "profile", session.getAccessProfile().name()));
        StartResult result = workflowOrchestrator.startSessionWorkflow(
            session,
            () -> {
                if (session.getStatus() == ObfuscationSession.Status.COMPLETED && session.getOutputJarPath() != null) {
                    File outputFile = new File(session.getOutputJarPath());
                    if (outputFile.exists()) {
                        String outputKey = artifactStore.createManagedObjectKey(outputFile.getName(), "output");
                        String storedKey = storeLocalFile(outputKey, outputFile);
                        sessionService.linkOutputArtifact(session, storedKey);
                    }
                }
                return kotlin.Unit.INSTANCE;
            },
            null
        );

        if (result == StartResult.Busy) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Another obfuscation task is already running");
        }
        if (result == StartResult.MissingConfig) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No config uploaded");
        }
        if (result == StartResult.MissingInput) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No input JAR uploaded");
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "started");
        return response;
    }

    public ResponseEntity<Resource> download(String sessionId, Authentication authentication) {
        ObfuscationSession session = sessionService.getSession(sessionId);
        PersistedSessionState state = findSessionState(sessionId);
        if (session == null && state == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
        }
        if (session != null) {
            requireSessionAccess(session, authentication);
        } else {
            requireSessionAccess(state, authentication);
        }
        String outputPath = session == null ? null : session.getOutputJarPath();
        File file = outputPath == null ? null : new File(outputPath);
        String outputObjectKey = session != null ? session.getOutputObjectKey() : state.getOutputObjectKey();
        if ((file == null || !file.exists()) && outputObjectKey != null) {
            file = artifactStore.getObject(outputObjectKey);
        }
        if (file == null || !file.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No output artifact available");
        }
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + file.getName())
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(new FileSystemResource(file));
    }

    public Map<String, Object> projectMeta(String sessionId, String scopeValue, Authentication authentication) {
        ObfuscationSession session = requireSession(sessionId, authentication);
        ObfuscationSession.ProjectScope scope = parseScope(scopeValue);
        ProjectMeta meta = workerGateway.projectMeta(session, scope);
        Map<String, Object> result = ApiSupport.ok();
        result.put("scope", meta.getScope());
        result.put("available", meta.getAvailable());
        result.put("classCount", meta.getClassCount());
        return result;
    }

    public Map<String, Object> projectTree(String sessionId, String scopeValue, Authentication authentication) {
        ObfuscationSession session = requireSession(sessionId, authentication);
        policyService.requireProjectPreview(session);
        ObfuscationSession.ProjectScope scope = parseScope(scopeValue);
        ProjectTree tree;
        try {
            tree = workerGateway.projectTree(session, scope);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No " + scope.name().toLowerCase() + " class structure available");
        }
        Map<String, Object> result = ApiSupport.ok();
        result.put("scope", tree.getScope());
        result.put("classCount", tree.getClassCount());
        result.put("classes", tree.getClasses());
        return result;
    }

    public Map<String, Object> projectSource(String sessionId, String scopeValue, String className, Authentication authentication) {
        ObfuscationSession session = requireSession(sessionId, authentication);
        policyService.requireSourcePreview(session);
        ObfuscationSession.ProjectScope scope = parseScope(scopeValue);
        if (!WebBridgeSupport.isValidClassName(className)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid class name");
        }
        ProjectSource source;
        try {
            source = workerGateway.projectSource(session, scope, className);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Class not found");
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No " + scope.name().toLowerCase() + " JAR available");
        }
        Map<String, Object> result = ApiSupport.ok();
        result.put("scope", source.getScope());
        result.put("class", source.getClassName());
        result.put("language", source.getLanguage());
        result.put("code", source.getCode());
        return result;
    }

    public ObfuscationSession requireSession(String sessionId, Authentication authentication) {
        ObfuscationSession session = ApiSupport.requireSession(sessionService, sessionId);
        requireSessionAccess(session, authentication);
        return session;
    }

    public ObfuscationSession requireEditable(String sessionId, Authentication authentication) {
        ObfuscationSession session = requireSession(sessionId, authentication);
        ApiSupport.ensureEditable(session);
        return session;
    }

    public ObfuscationSession requireSession(String sessionId) {
        return ApiSupport.requireSession(sessionService, sessionId);
    }

    public ObfuscationSession requireEditable(String sessionId) {
        ObfuscationSession session = requireSession(sessionId);
        ApiSupport.ensureEditable(session);
        return session;
    }

    private ObfuscationSession.ProjectScope parseScope(String scopeValue) {
        ObfuscationSession.ProjectScope scope = WebBridgeSupport.parseProjectScope(scopeValue);
        if (scope == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid scope");
        }
        return scope;
    }

    private String storeLocalFile(String objectKey, File file) {
        try {
            artifactStore.putObject(objectKey, java.nio.file.Files.readAllBytes(file.toPath()));
            return objectKey;
        } catch (java.io.IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to externalize artifact");
        }
    }

    private void rehydrateSessionArtifacts(ObfuscationSession session) {
        if ((session.getConfigFilePath() == null || !(new File(session.getConfigFilePath()).exists()))
            && session.getConfigObjectKey() != null && !session.getConfigObjectKey().isBlank()) {
            File configFile = artifactStore.getObject(session.getConfigObjectKey());
            try {
                String jsonText = java.nio.file.Files.readString(configFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                facadeRehydrateConfig(session, configFile.getName(), jsonText);
            } catch (java.io.IOException exception) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to restore config artifact");
            }
        }
        if ((session.getInputJarPath() == null || !(new File(session.getInputJarPath()).exists()))
            && session.getInputObjectKey() != null && !session.getInputObjectKey().isBlank()) {
            File inputFile = artifactStore.getObject(session.getInputObjectKey());
            sessionService.saveInput(session, inputFile);
            sessionService.linkInputArtifact(session, session.getInputObjectKey());
        }
        session.getLibraryObjectRefs().forEach((fileName, objectKey) -> {
            File localFile = new File(session.getLibrariesDir(), fileName);
            if (!localFile.exists()) {
                File restored = artifactStore.getObject(objectKey);
                sessionService.saveLibraries(session, java.util.List.of(restored));
                sessionService.linkLibraryArtifact(session, fileName, objectKey);
            }
        });
        session.getAssetObjectRefs().forEach((fileName, objectKey) -> {
            File localFile = new File(session.getAssetsDir(), fileName);
            if (!localFile.exists()) {
                File restored = artifactStore.getObject(objectKey);
                sessionService.saveAssets(session, java.util.List.of(restored));
                sessionService.linkAssetArtifact(session, fileName, objectKey);
            }
        });
    }

    private void facadeRehydrateConfig(ObfuscationSession session, String fileName, String jsonText) {
        com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(jsonText).getAsJsonObject();
        sessionService.saveConfig(session, json, fileName);
        sessionService.linkConfigArtifact(session, session.getConfigObjectKey());
    }

    private PersistedSessionState findSessionState(String sessionId) {
        PersistedSessionState persisted = sessionMetadataQuery.findSession(sessionId);
        if (persisted != null) {
            return persisted;
        }
        ObfuscationSession live = sessionService.getSession(sessionId);
        if (live == null) {
            return null;
        }
        return new PersistedSessionState(
            live.getId(),
            live.getOwnerUsername(),
            live.getAccessProfile().name(),
            live.getControlPlane(),
            live.getWorkerPlane(),
            live.getStatus().name(),
            live.getCurrentStep(),
            live.getProgress(),
            live.getTotalSteps(),
            live.getErrorMessage(),
            live.getConfigDisplayName(),
            live.getInputDisplayName(),
            live.getOutputJarPath() == null ? null : new File(live.getOutputJarPath()).getName(),
            live.getConfigObjectKey(),
            live.getInputObjectKey(),
            live.getOutputObjectKey(),
            live.getLibraryNames(),
            live.getAssetNames(),
            live.getLibraryObjectRefs(),
            live.getAssetObjectRefs()
        );
    }

    private void requireSessionAccess(ObfuscationSession session, Authentication authentication) {
        if (isPrivileged(authentication)) {
            return;
        }
        String ownerUsername = session.getOwnerUsername();
        String currentUsername = ApiSupport.requireUsername(authentication);
        if (ownerUsername == null || !ownerUsername.equals(currentUsername)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Session is not accessible");
        }
    }

    private void requireSessionAccess(PersistedSessionState state, Authentication authentication) {
        if (isPrivileged(authentication)) {
            return;
        }
        String currentUsername = ApiSupport.requireUsername(authentication);
        if (state.getOwnerUsername() == null || !state.getOwnerUsername().equals(currentUsername)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Session is not accessible");
        }
    }

    private boolean isPrivileged(Authentication authentication) {
        return ApiSupport.hasAnyRole(authentication, "PLATFORM_ADMIN", "SUPER_ADMIN");
    }
}
