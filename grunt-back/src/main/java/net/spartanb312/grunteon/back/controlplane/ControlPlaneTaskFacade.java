package net.spartanb312.grunteon.back.controlplane;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import net.spartanb312.grunteon.obfuscator.web.ArtifactMetadataQuery;
import net.spartanb312.grunteon.back.controlplane.artifact.ArtifactDownloadGrantService;
import net.spartanb312.grunteon.back.controlplane.artifact.ArtifactStore;
import net.spartanb312.grunteon.back.controlplane.events.TaskEventPublisher;
import net.spartanb312.grunteon.back.controlplane.telemetry.ControlPlaneTelemetry;
import net.spartanb312.grunteon.back.policy.ControlPlanePolicyService;
import net.spartanb312.grunteon.back.support.ApiSupport;
import net.spartanb312.grunteon.obfuscator.web.ObfuscationSession;
import net.spartanb312.grunteon.obfuscator.web.PlatformTaskRecord;
import net.spartanb312.grunteon.obfuscator.web.PlatformTaskService;
import net.spartanb312.grunteon.obfuscator.web.ProjectInspectionService;
import net.spartanb312.grunteon.obfuscator.web.ProjectMeta;
import net.spartanb312.grunteon.obfuscator.web.ProjectSource;
import net.spartanb312.grunteon.obfuscator.web.ProjectTree;
import net.spartanb312.grunteon.obfuscator.web.PersistedSessionState;
import net.spartanb312.grunteon.obfuscator.web.PersistedTaskState;
import net.spartanb312.grunteon.obfuscator.web.SessionAccessProfile;
import net.spartanb312.grunteon.obfuscator.web.SessionMetadataQuery;
import net.spartanb312.grunteon.obfuscator.web.SessionService;
import net.spartanb312.grunteon.obfuscator.web.TaskMetadataQuery;
import net.spartanb312.grunteon.obfuscator.web.WebBridgeSupport;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ControlPlaneTaskFacade {

    private final PlatformTaskService platformTaskService;
    private final SessionService sessionService;
    private final ArtifactStore artifactStore;
    private final ArtifactDownloadGrantService artifactDownloadGrantService;
    private final ControlPlanePolicyService policyService;
    private final ProjectInspectionService projectInspectionService;
    private final TaskEventPublisher taskEventPublisher;
    private final ControlPlaneTelemetry telemetry;
    private final TaskMetadataQuery taskMetadataQuery;
    private final SessionMetadataQuery sessionMetadataQuery;
    private final ArtifactMetadataQuery artifactMetadataQuery;

    public ControlPlaneTaskFacade(
        PlatformTaskService platformTaskService,
        SessionService sessionService,
        ArtifactStore artifactStore,
        ArtifactDownloadGrantService artifactDownloadGrantService,
        ControlPlanePolicyService policyService,
        ProjectInspectionService projectInspectionService,
        TaskEventPublisher taskEventPublisher,
        ControlPlaneTelemetry telemetry,
        TaskMetadataQuery taskMetadataQuery,
        SessionMetadataQuery sessionMetadataQuery,
        ArtifactMetadataQuery artifactMetadataQuery
    ) {
        this.platformTaskService = platformTaskService;
        this.sessionService = sessionService;
        this.artifactStore = artifactStore;
        this.artifactDownloadGrantService = artifactDownloadGrantService;
        this.policyService = policyService;
        this.projectInspectionService = projectInspectionService;
        this.taskEventPublisher = taskEventPublisher;
        this.telemetry = telemetry;
        this.taskMetadataQuery = taskMetadataQuery;
        this.sessionMetadataQuery = sessionMetadataQuery;
        this.artifactMetadataQuery = artifactMetadataQuery;
    }

    public Map<String, Object> createTask(Map<String, Object> request, boolean uiClient) {
        String projectName = stringValue(request.get("projectName"), "Grunteon Task");
        String inputObjectKey = stringValue(request.get("inputObjectKey"), null);
        if (inputObjectKey == null || inputObjectKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing inputObjectKey");
        }
        String configObjectKey = stringValue(request.get("configObjectKey"), null);
        SessionAccessProfile profile = policyService.resolveProfile(stringValue(request.get("policyProfile"), null), uiClient);
        PlatformTaskRecord task = platformTaskService.createTask(projectName, inputObjectKey, configObjectKey, profile);
        telemetry.record("control.task.create", Map.of("taskId", task.getId(), "profile", profile.name()));
        taskEventPublisher.publish("control.task.create", Map.of("taskId", task.getId(), "profile", profile.name()));
        return buildTaskResponse(task);
    }

    public Map<String, Object> listTasks() {
        Map<String, Object> result = ApiSupport.ok();
        result.put(
            "tasks",
            taskMetadataQuery.loadTasks().stream()
                .map(this::buildTaskMap)
                .toList()
        );
        return result;
    }

    public Map<String, Object> getTask(String taskId) {
        PersistedTaskState task = requireTaskState(taskId);
        return buildTaskResponse(task);
    }

    public Map<String, Object> startTask(String taskId) {
        telemetry.record("control.task.start", Map.of("taskId", taskId));
        taskEventPublisher.publish("control.task.start", Map.of("taskId", taskId));
        return buildTaskResponse(platformTaskService.startTask(taskId));
    }

    public Map<String, Object> stages(String taskId) {
        PersistedTaskState task = requireTaskState(taskId);
        Map<String, Object> result = ApiSupport.ok();
        result.put("stages", task.getStages().stream().map(ApiSupport::buildTaskStageJson).toList());
        return result;
    }

    public Map<String, Object> logs(String taskId) {
        PersistedTaskState task = requireTaskState(taskId);
        Map<String, Object> result = ApiSupport.ok();
        ObfuscationSession session = task.getSessionId() == null ? null : sessionService.getSession(task.getSessionId());
        if (session != null) {
            result.put("logs", policyService.filterLogs(session));
        } else {
            result.put("logs", task.getLogs());
        }
        return result;
    }

    public Map<String, Object> downloadUrl(String taskId) {
        PersistedTaskState task = requireTaskState(taskId);
        if (task.getOutputObjectKey() == null || task.getOutputObjectKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No output object available");
        }
        File file = artifactStore.getObject(task.getOutputObjectKey());
        ArtifactDownloadGrantService.ArtifactDownloadGrant grant = artifactDownloadGrantService.issueGrant(
            task.getOutputObjectKey(),
            file.getName()
        );
        Map<String, Object> result = ApiSupport.ok();
        result.put("method", grant.getMethod());
        result.put("downloadUrl", grant.getUrl());
        result.put("expiresAt", grant.getExpiresAt().toString());
        result.put("grantType", "ONE_TIME");
        return result;
    }

    public ResponseEntity<Resource> download(String taskId) {
        PersistedTaskState task = requireTaskState(taskId);
        if (task.getOutputObjectKey() == null || task.getOutputObjectKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No output object available");
        }
        SessionAccessProfile profile = SessionAccessProfile.parseOrNull(task.getPolicyMode());
        if (profile == null) {
            profile = SessionAccessProfile.SECURE;
        }
        if (!profile.getAllowDetailedLogs()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Direct artifact download is disabled in secure mode");
        }
        File file = artifactStore.getObject(task.getOutputObjectKey());
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + file.getName())
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(new FileSystemResource(file));
    }

    public Map<String, Object> artifacts(String taskId) {
        PersistedTaskState task = requireTaskState(taskId);
        Map<String, Object> result = ApiSupport.ok();
        result.put(
            "artifacts",
            artifactMetadataQuery.loadArtifactsForOwner("TASK", taskId).stream()
                .map(WebBridgeSupport::buildArtifactMap)
                .map(map -> policyService.filterArtifactMap(task.getPolicyMode(), map))
                .toList()
        );
        return result;
    }

    public Map<String, Object> projectMeta(String taskId, String scopeValue) {
        ObfuscationSession session = resolveTaskSession(taskId);
        ObfuscationSession.ProjectScope scope = parseScope(scopeValue);
        ProjectMeta meta = projectInspectionService.projectMeta(session, scope);
        Map<String, Object> result = ApiSupport.ok();
        result.put("scope", meta.getScope());
        result.put("available", meta.getAvailable());
        result.put("classCount", meta.getClassCount());
        return result;
    }

    public Map<String, Object> projectTree(String taskId, String scopeValue) {
        ObfuscationSession session = resolveTaskSession(taskId);
        policyService.requireProjectPreview(session);
        ObfuscationSession.ProjectScope scope = parseScope(scopeValue);
        ProjectTree tree;
        try {
            tree = projectInspectionService.projectTree(session, scope);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No " + scope.name().toLowerCase() + " class structure available");
        }
        Map<String, Object> result = ApiSupport.ok();
        result.put("scope", tree.getScope());
        result.put("classCount", tree.getClassCount());
        result.put("classes", tree.getClasses());
        return result;
    }

    public Map<String, Object> projectSource(String taskId, String scopeValue, String className) {
        ObfuscationSession session = resolveTaskSession(taskId);
        policyService.requireSourcePreview(session);
        ObfuscationSession.ProjectScope scope = parseScope(scopeValue);
        if (!WebBridgeSupport.isValidClassName(className)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid class name");
        }
        ProjectSource source;
        try {
            source = projectInspectionService.projectSource(session, scope, className);
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

    private Map<String, Object> buildTaskResponse(PlatformTaskRecord task) {
        Map<String, Object> result = ApiSupport.ok();
        result.put("task", policyService.filterTaskMap(task, ApiSupport.buildTaskJson(task, sessionService)));
        result.put("logs", task.getSessionId() == null ? task.getLogs() : policyService.filterLogs(resolveTaskSession(task.getId())));
        return result;
    }

    private Map<String, Object> buildTaskResponse(PersistedTaskState task) {
        Map<String, Object> result = ApiSupport.ok();
        result.put("task", policyService.filterTaskMap(task.getPolicyMode(), buildTaskMap(task)));
        ObfuscationSession session = task.getSessionId() == null ? null : sessionService.getSession(task.getSessionId());
        if (session != null) {
            result.put("logs", policyService.filterLogs(session));
        } else {
            result.put("logs", task.getLogs());
        }
        return result;
    }

    private Map<String, Object> buildTaskMap(PersistedTaskState task) {
        PersistedSessionState sessionState = task.getSessionId() == null ? null : sessionMetadataQuery.findSession(task.getSessionId());
        return ApiSupport.buildTaskJson(task, sessionState);
    }

    private PersistedTaskState requireTaskState(String taskId) {
        PersistedTaskState task = findTaskState(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        return task;
    }

    private PersistedTaskState findTaskState(String taskId) {
        PersistedTaskState persisted = taskMetadataQuery.findTask(taskId);
        if (persisted != null) {
            return persisted;
        }
        PlatformTaskRecord live = platformTaskService.findLiveTask(taskId);
        return live == null ? null : new PersistedTaskState(
            live.getId(),
            live.getProjectName(),
            live.getInputObjectKey(),
            live.getConfigObjectKey(),
            live.getOutputObjectKey(),
            live.getSessionId(),
            live.getAccessProfile().name(),
            live.getStatus().name(),
            live.getCurrentStage(),
            live.getProgress(),
            live.getMessage(),
            List.copyOf(live.getLogs()),
            List.copyOf(live.getStages()),
            live.getRecoveryPreviousStatus(),
            live.getRecoveryReason(),
            live.getRecoveredAt(),
            live.getCreatedAt(),
            live.getUpdatedAt()
        );
    }

    private ObfuscationSession resolveTaskSession(String taskId) {
        PlatformTaskRecord task = platformTaskService.getTask(taskId);
        String sessionId = task.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task session not available");
        }
        return ApiSupport.requireSession(sessionService, sessionId);
    }

    private ObfuscationSession.ProjectScope parseScope(String scopeValue) {
        ObfuscationSession.ProjectScope scope = WebBridgeSupport.parseProjectScope(scopeValue);
        if (scope == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid scope");
        }
        return scope;
    }

    private String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String string = String.valueOf(value);
        if (string.isBlank() || "null".equals(string)) {
            return fallback;
        }
        return string;
    }
}
