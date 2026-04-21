package net.spartanb312.grunteon.back.controlplane;

import java.io.File;
import java.util.Map;
import java.util.NoSuchElementException;
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
import net.spartanb312.grunteon.obfuscator.web.SessionAccessProfile;
import net.spartanb312.grunteon.obfuscator.web.SessionService;
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

    public ControlPlaneTaskFacade(
        PlatformTaskService platformTaskService,
        SessionService sessionService,
        ArtifactStore artifactStore,
        ArtifactDownloadGrantService artifactDownloadGrantService,
        ControlPlanePolicyService policyService,
        ProjectInspectionService projectInspectionService,
        TaskEventPublisher taskEventPublisher,
        ControlPlaneTelemetry telemetry
    ) {
        this.platformTaskService = platformTaskService;
        this.sessionService = sessionService;
        this.artifactStore = artifactStore;
        this.artifactDownloadGrantService = artifactDownloadGrantService;
        this.policyService = policyService;
        this.projectInspectionService = projectInspectionService;
        this.taskEventPublisher = taskEventPublisher;
        this.telemetry = telemetry;
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
            platformTaskService.listTasks().stream()
                .map(task -> ApiSupport.buildTaskJson(task, sessionService))
                .toList()
        );
        return result;
    }

    public Map<String, Object> getTask(String taskId) {
        return buildTaskResponse(platformTaskService.getTask(taskId));
    }

    public Map<String, Object> startTask(String taskId) {
        telemetry.record("control.task.start", Map.of("taskId", taskId));
        taskEventPublisher.publish("control.task.start", Map.of("taskId", taskId));
        return buildTaskResponse(platformTaskService.startTask(taskId));
    }

    public Map<String, Object> stages(String taskId) {
        PlatformTaskRecord task = platformTaskService.getTask(taskId);
        Map<String, Object> result = ApiSupport.ok();
        result.put("stages", task.getStages().stream().map(ApiSupport::buildTaskStageJson).toList());
        return result;
    }

    public Map<String, Object> logs(String taskId) {
        ObfuscationSession session = resolveTaskSession(taskId);
        Map<String, Object> result = ApiSupport.ok();
        result.put("logs", policyService.filterLogs(session));
        return result;
    }

    public Map<String, Object> downloadUrl(String taskId) {
        PlatformTaskRecord task = platformTaskService.getTask(taskId);
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
        PlatformTaskRecord task = platformTaskService.getTask(taskId);
        if (task.getOutputObjectKey() == null || task.getOutputObjectKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No output object available");
        }
        if (!task.getAccessProfile().getAllowDetailedLogs()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Direct artifact download is disabled in secure mode");
        }
        File file = artifactStore.getObject(task.getOutputObjectKey());
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + file.getName())
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(new FileSystemResource(file));
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
