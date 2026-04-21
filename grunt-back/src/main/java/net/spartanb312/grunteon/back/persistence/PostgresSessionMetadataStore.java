package net.spartanb312.grunteon.back.persistence;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.spartanb312.grunteon.obfuscator.web.ObfuscationSession;
import net.spartanb312.grunteon.obfuscator.web.PersistedSessionState;
import net.spartanb312.grunteon.obfuscator.web.SessionMetadataQuery;
import net.spartanb312.grunteon.obfuscator.web.SessionMetadataStore;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.Parameter;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class PostgresSessionMetadataStore implements SessionMetadataStore, SessionMetadataQuery {

    private final DatabaseClient databaseClient;
    private final Gson gson = new Gson();
    private final Type stringListType = new TypeToken<List<String>>() { }.getType();
    private final Type stringMapType = new TypeToken<Map<String, String>>() { }.getType();

    public PostgresSessionMetadataStore(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public void saveSession(ObfuscationSession session) {
        updateSession(session)
            .flatMap(rows -> rows != null && rows > 0
                ? Mono.just(rows)
                : insertSession(session).onErrorResume(error -> updateSession(session))
            )
            .onErrorResume(error -> Mono.empty())
            .subscribe();
    }

    private Mono<Long> updateSession(ObfuscationSession session) {
        return databaseClient.sql(
                """
                UPDATE control_session_state
                SET policy_mode = :policyMode,
                    control_plane = :controlPlane,
                    worker_plane = :workerPlane,
                    status = :status,
                    current_step = :currentStep,
                    progress = :progress,
                    total_steps = :totalSteps,
                    error_message = :errorMessage,
                    config_file_name = :configFileName,
                    input_file_name = :inputFileName,
                    output_file_name = :outputFileName,
                    config_object_key = :configObjectKey,
                    input_object_key = :inputObjectKey,
                    output_object_key = :outputObjectKey,
                    library_files_json = :libraryFilesJson,
                    asset_files_json = :assetFilesJson,
                    library_object_refs_json = :libraryObjectRefsJson,
                    asset_object_refs_json = :assetObjectRefsJson,
                    updated_at = :updatedAt
                WHERE session_id = :sessionId
                """
            )
            .bind("policyMode", session.getAccessProfile().name())
            .bind("controlPlane", session.getControlPlane())
            .bind("workerPlane", session.getWorkerPlane())
            .bind("status", session.getStatus().name())
            .bind("currentStep", session.getCurrentStep())
            .bind("progress", session.getProgress())
            .bind("totalSteps", session.getTotalSteps())
            .bind("errorMessage", Parameter.fromOrEmpty(session.getErrorMessage(), String.class))
            .bind("configFileName", Parameter.fromOrEmpty(session.getConfigDisplayName(), String.class))
            .bind("inputFileName", Parameter.fromOrEmpty(session.getInputDisplayName(), String.class))
            .bind("outputFileName", Parameter.fromOrEmpty(outputFileName(session), String.class))
            .bind("configObjectKey", Parameter.fromOrEmpty(session.getConfigObjectKey(), String.class))
            .bind("inputObjectKey", Parameter.fromOrEmpty(session.getInputObjectKey(), String.class))
            .bind("outputObjectKey", Parameter.fromOrEmpty(session.getOutputObjectKey(), String.class))
            .bind("libraryFilesJson", gson.toJson(session.getLibraryNames()))
            .bind("assetFilesJson", gson.toJson(session.getAssetNames()))
            .bind("libraryObjectRefsJson", gson.toJson(session.getLibraryObjectRefs()))
            .bind("assetObjectRefsJson", gson.toJson(session.getAssetObjectRefs()))
            .bind("updatedAt", java.time.Instant.now().toString())
            .bind("sessionId", session.getId())
            .fetch()
            .rowsUpdated();
    }

    private Mono<Long> insertSession(ObfuscationSession session) {
        return databaseClient.sql(
                """
                INSERT INTO control_session_state (
                    session_id,
                    policy_mode,
                    control_plane,
                    worker_plane,
                    status,
                    current_step,
                    progress,
                    total_steps,
                    error_message,
                    config_file_name,
                    input_file_name,
                    output_file_name,
                    config_object_key,
                    input_object_key,
                    output_object_key,
                    library_files_json,
                    asset_files_json,
                    library_object_refs_json,
                    asset_object_refs_json,
                    created_at,
                    updated_at
                ) VALUES (
                    :sessionId,
                    :policyMode,
                    :controlPlane,
                    :workerPlane,
                    :status,
                    :currentStep,
                    :progress,
                    :totalSteps,
                    :errorMessage,
                    :configFileName,
                    :inputFileName,
                    :outputFileName,
                    :configObjectKey,
                    :inputObjectKey,
                    :outputObjectKey,
                    :libraryFilesJson,
                    :assetFilesJson,
                    :libraryObjectRefsJson,
                    :assetObjectRefsJson,
                    :createdAt,
                    :updatedAt
                )
                """
            )
            .bind("sessionId", session.getId())
            .bind("policyMode", session.getAccessProfile().name())
            .bind("controlPlane", session.getControlPlane())
            .bind("workerPlane", session.getWorkerPlane())
            .bind("status", session.getStatus().name())
            .bind("currentStep", session.getCurrentStep())
            .bind("progress", session.getProgress())
            .bind("totalSteps", session.getTotalSteps())
            .bind("errorMessage", Parameter.fromOrEmpty(session.getErrorMessage(), String.class))
            .bind("configFileName", Parameter.fromOrEmpty(session.getConfigDisplayName(), String.class))
            .bind("inputFileName", Parameter.fromOrEmpty(session.getInputDisplayName(), String.class))
            .bind("outputFileName", Parameter.fromOrEmpty(outputFileName(session), String.class))
            .bind("configObjectKey", Parameter.fromOrEmpty(session.getConfigObjectKey(), String.class))
            .bind("inputObjectKey", Parameter.fromOrEmpty(session.getInputObjectKey(), String.class))
            .bind("outputObjectKey", Parameter.fromOrEmpty(session.getOutputObjectKey(), String.class))
            .bind("libraryFilesJson", gson.toJson(session.getLibraryNames()))
            .bind("assetFilesJson", gson.toJson(session.getAssetNames()))
            .bind("libraryObjectRefsJson", gson.toJson(session.getLibraryObjectRefs()))
            .bind("assetObjectRefsJson", gson.toJson(session.getAssetObjectRefs()))
            .bind("createdAt", java.time.Instant.now().toString())
            .bind("updatedAt", java.time.Instant.now().toString())
            .fetch()
            .rowsUpdated();
    }

    @Override
    public List<PersistedSessionState> loadSessions() {
        try {
            return databaseClient.sql(
                    """
                    SELECT session_id, policy_mode, control_plane, worker_plane, status, current_step,
                           progress, total_steps, error_message, config_file_name, input_file_name,
                           output_file_name, config_object_key, input_object_key, output_object_key,
                           library_files_json, asset_files_json, library_object_refs_json, asset_object_refs_json
                    FROM control_session_state
                    ORDER BY updated_at DESC
                    """
                )
                .map((row, metadata) -> new PersistedSessionState(
                    row.get("session_id", String.class),
                    row.get("policy_mode", String.class),
                    row.get("control_plane", String.class),
                    row.get("worker_plane", String.class),
                    row.get("status", String.class),
                    row.get("current_step", String.class),
                    row.get("progress", Integer.class),
                    row.get("total_steps", Integer.class),
                    row.get("error_message", String.class),
                    row.get("config_file_name", String.class),
                    row.get("input_file_name", String.class),
                    row.get("output_file_name", String.class),
                    row.get("config_object_key", String.class),
                    row.get("input_object_key", String.class),
                    row.get("output_object_key", String.class),
                    parseStringList(row.get("library_files_json", String.class)),
                    parseStringList(row.get("asset_files_json", String.class)),
                    parseStringMap(row.get("library_object_refs_json", String.class)),
                    parseStringMap(row.get("asset_object_refs_json", String.class))
                ))
                .all()
                .collectList()
                .blockOptional()
                .orElseGet(Collections::emptyList);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    @Override
    public PersistedSessionState findSession(String sessionId) {
        try {
            return databaseClient.sql(
                    """
                    SELECT session_id, policy_mode, control_plane, worker_plane, status, current_step,
                           progress, total_steps, error_message, config_file_name, input_file_name,
                           output_file_name, config_object_key, input_object_key, output_object_key,
                           library_files_json, asset_files_json, library_object_refs_json, asset_object_refs_json
                    FROM control_session_state
                    WHERE session_id = :sessionId
                    """
                )
                .bind("sessionId", sessionId)
                .map((row, metadata) -> new PersistedSessionState(
                    row.get("session_id", String.class),
                    row.get("policy_mode", String.class),
                    row.get("control_plane", String.class),
                    row.get("worker_plane", String.class),
                    row.get("status", String.class),
                    row.get("current_step", String.class),
                    row.get("progress", Integer.class),
                    row.get("total_steps", Integer.class),
                    row.get("error_message", String.class),
                    row.get("config_file_name", String.class),
                    row.get("input_file_name", String.class),
                    row.get("output_file_name", String.class),
                    row.get("config_object_key", String.class),
                    row.get("input_object_key", String.class),
                    row.get("output_object_key", String.class),
                    parseStringList(row.get("library_files_json", String.class)),
                    parseStringList(row.get("asset_files_json", String.class)),
                    parseStringMap(row.get("library_object_refs_json", String.class)),
                    parseStringMap(row.get("asset_object_refs_json", String.class))
                ))
                .one()
                .blockOptional()
                .orElse(null);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String outputFileName(ObfuscationSession session) {
        String path = session.getOutputJarPath();
        if (path == null || path.isBlank()) {
            return null;
        }
        return new java.io.File(path).getName();
    }

    private List<String> parseStringList(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return List.of();
        }
        List<String> parsed = gson.fromJson(rawJson, stringListType);
        return parsed == null ? List.of() : parsed;
    }

    private Map<String, String> parseStringMap(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Map.of();
        }
        Map<String, String> parsed = gson.fromJson(rawJson, stringMapType);
        return parsed == null ? Map.of() : parsed;
    }
}
