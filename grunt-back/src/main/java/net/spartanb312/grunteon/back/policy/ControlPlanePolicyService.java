package net.spartanb312.grunteon.back.policy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.spartanb312.grunteon.back.config.BackPolicyProperties;
import net.spartanb312.grunteon.obfuscator.web.ObfuscationSession;
import net.spartanb312.grunteon.obfuscator.web.PlatformTaskRecord;
import net.spartanb312.grunteon.obfuscator.web.SessionAccessProfile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ControlPlanePolicyService {

    private final BackPolicyProperties properties;

    public ControlPlanePolicyService(BackPolicyProperties properties) {
        this.properties = properties;
    }

    public SessionAccessProfile resolveProfile(String requestedProfile, boolean uiClient) {
        SessionAccessProfile parsed = SessionAccessProfile.parseOrNull(requestedProfile);
        if (parsed != null) {
            return parsed;
        }
        return uiClient ? properties.getUiProfile() : properties.getDefaultProfile();
    }

    public List<String> filterLogs(ObfuscationSession session) {
        if (session.getAccessProfile().getAllowDetailedLogs()) {
            return session.getConsoleLogs();
        }
        return session.getConsoleLogs().stream()
            .map(this::sanitizeLogLine)
            .toList();
    }

    public void requireProjectPreview(ObfuscationSession session) {
        if (!session.getAccessProfile().getAllowProjectPreview()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project tree preview is disabled in secure mode");
        }
    }

    public void requireSourcePreview(ObfuscationSession session) {
        if (!session.getAccessProfile().getAllowSourcePreview()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Source preview is disabled in secure mode");
        }
    }

    public Map<String, Object> buildProfileSummary(SessionAccessProfile profile) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", profile.name());
        result.put("allowProjectPreview", profile.getAllowProjectPreview());
        result.put("allowSourcePreview", profile.getAllowSourcePreview());
        result.put("allowDetailedLogs", profile.getAllowDetailedLogs());
        return result;
    }

    public Map<String, Object> describePlatformProfiles() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("defaultProfile", buildProfileSummary(properties.getDefaultProfile()));
        result.put("uiProfile", buildProfileSummary(properties.getUiProfile()));
        result.put(
            "availableProfiles",
            List.of(
                buildProfileSummary(SessionAccessProfile.SECURE),
                buildProfileSummary(SessionAccessProfile.RESEARCH)
            )
        );
        result.put("controlPlane", properties.getControlPlaneName());
        result.put("workerPlane", properties.getWorkerPlaneName());
        return result;
    }

    public Map<String, Object> filterSessionMap(ObfuscationSession session, Map<String, Object> full) {
        if (session.getAccessProfile().getAllowDetailedLogs()) {
            return full;
        }
        Map<String, Object> result = new LinkedHashMap<>(full);
        result.remove("configFileName");
        result.remove("configObjectKey");
        result.remove("inputFileName");
        result.remove("inputObjectKey");
        result.remove("outputObjectKey");
        result.remove("libraryFiles");
        result.remove("libraryObjectRefs");
        result.remove("assetFiles");
        result.remove("assetObjectRefs");
        Object error = result.get("error");
        if (error != null) {
            result.put("error", "Task failed");
        }
        return result;
    }

    public Map<String, Object> filterTaskMap(PlatformTaskRecord task, Map<String, Object> full) {
        if (task.getAccessProfile().getAllowDetailedLogs()) {
            return full;
        }
        Map<String, Object> result = new LinkedHashMap<>(full);
        result.remove("inputObjectKey");
        result.remove("configObjectKey");
        result.remove("outputObjectKey");
        result.remove("sessionId");
        result.remove("session");
        result.remove("inputClassCount");
        result.remove("outputClassCount");
        String status = String.valueOf(result.getOrDefault("status", ""));
        if ("FAILED".equals(status)) {
            result.put("message", "Task failed");
        } else if ("COMPLETED".equals(status)) {
            result.put("message", "Task completed");
        } else {
            result.put("message", "Task running");
        }
        return result;
    }

    private String sanitizeLogLine(String line) {
        if (line == null || line.isBlank()) {
            return "";
        }
        String compact = line.replaceAll("[A-Za-z]:\\\\[^\\s]+", "[path]")
            .replaceAll("/[^\\s]+", "[path]");
        if (compact.contains("ERROR")) {
            return compact;
        }
        if (compact.contains("WARN")) {
            return compact;
        }
        if (compact.contains("Finished in")) {
            return compact;
        }
        return "[secure-log] worker event captured";
    }
}
