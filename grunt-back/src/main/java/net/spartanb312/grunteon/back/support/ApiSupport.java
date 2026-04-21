package net.spartanb312.grunteon.back.support;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import net.spartanb312.grunteon.obfuscator.web.ObfuscationSession;
import net.spartanb312.grunteon.obfuscator.web.PersistedSessionState;
import net.spartanb312.grunteon.obfuscator.web.PersistedTaskState;
import net.spartanb312.grunteon.obfuscator.web.PlatformTaskRecord;
import net.spartanb312.grunteon.obfuscator.web.SessionService;
import net.spartanb312.grunteon.obfuscator.web.TaskStageRecord;
import net.spartanb312.grunteon.obfuscator.web.WebBridgeSupport;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class ApiSupport {

    private static final Gson gson = new Gson();

    private ApiSupport() {
    }

    public static Map<String, Object> ok() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        return response;
    }

    public static Map<String, Object> errorBody(String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "error");
        response.put("message", message);
        return response;
    }

    public static ObfuscationSession requireSession(SessionService sessionService, String sessionId) {
        ObfuscationSession session = sessionService.getSession(sessionId);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
        }
        return session;
    }

    public static void ensureEditable(ObfuscationSession session) {
        if (session.getStatus() == ObfuscationSession.Status.RUNNING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session is currently running");
        }
    }

    public static String sanitizeFileName(String name, String fallback) {
        String raw = new File(name == null || name.isBlank() ? fallback : name.trim()).getName();
        String cleaned = raw.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return cleaned.isBlank() ? fallback : cleaned;
    }

    public static File uniqueFile(File dir, String preferredName) {
        String base = new File(preferredName).getName();
        if (base.isBlank()) {
            base = "upload.bin";
        }
        int dot = base.lastIndexOf('.');
        String name = dot > 0 ? base.substring(0, dot) : base;
        String ext = dot > 0 ? base.substring(dot) : "";
        File candidate = new File(dir, base);
        int index = 1;
        while (candidate.exists()) {
            candidate = new File(dir, name + "-" + index + ext);
            index++;
        }
        return candidate;
    }

    public static Map<String, Object> buildStatusResponse(ObfuscationSession session) {
        return new LinkedHashMap<>(WebBridgeSupport.buildStatusMap(session));
    }

    public static Map<String, Object> buildTaskJson(PlatformTaskRecord task, SessionService sessionService) {
        return new LinkedHashMap<>(WebBridgeSupport.buildTaskMap(task, sessionService));
    }

    public static Map<String, Object> buildTaskJson(PersistedTaskState task, PersistedSessionState sessionState) {
        return new LinkedHashMap<>(WebBridgeSupport.buildTaskMap(task, sessionState));
    }

    public static Map<String, Object> buildTaskStageJson(TaskStageRecord stage) {
        return new LinkedHashMap<>(WebBridgeSupport.buildTaskStageMap(stage));
    }

    public static Object gsonToJava(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return gson.fromJson(element, Object.class);
    }
}
