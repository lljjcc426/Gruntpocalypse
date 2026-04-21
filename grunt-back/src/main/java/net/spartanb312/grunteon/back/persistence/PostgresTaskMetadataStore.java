package net.spartanb312.grunteon.back.persistence;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.spartanb312.grunteon.obfuscator.web.PersistedTaskState;
import net.spartanb312.grunteon.obfuscator.web.PlatformTaskRecord;
import net.spartanb312.grunteon.obfuscator.web.TaskMetadataQuery;
import net.spartanb312.grunteon.obfuscator.web.TaskMetadataStore;
import net.spartanb312.grunteon.obfuscator.web.TaskStageRecord;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.Parameter;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class PostgresTaskMetadataStore implements TaskMetadataStore, TaskMetadataQuery {

    private final DatabaseClient databaseClient;
    private final Gson gson = new Gson();
    private final Type stringListType = new TypeToken<List<String>>() { }.getType();
    private final Type stageListType = new TypeToken<List<TaskStageRecord>>() { }.getType();

    public PostgresTaskMetadataStore(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public void saveTask(PlatformTaskRecord task) {
        updateTask(task)
            .flatMap(rows -> rows != null && rows > 0
                ? Mono.just(rows)
                : insertTask(task).onErrorResume(error -> updateTask(task))
            )
            .onErrorResume(error -> Mono.empty())
            .subscribe();
    }

    private Mono<Long> updateTask(PlatformTaskRecord task) {
        return databaseClient.sql(
                """
                UPDATE control_task_state
                SET project_name = :projectName,
                    input_object_key = :inputObjectKey,
                    config_object_key = :configObjectKey,
                    output_object_key = :outputObjectKey,
                    session_id = :sessionId,
                    policy_mode = :policyMode,
                    status = :status,
                    current_stage = :currentStage,
                    progress = :progress,
                    message = :message,
                    logs_json = :logsJson,
                    stages_json = :stagesJson,
                    recovery_previous_status = :recoveryPreviousStatus,
                    recovery_reason = :recoveryReason,
                    recovered_at = :recoveredAt,
                    updated_at = :updatedAt
                WHERE task_id = :taskId
                """
            )
            .bind("projectName", task.getProjectName())
            .bind("inputObjectKey", task.getInputObjectKey())
            .bind("configObjectKey", Parameter.fromOrEmpty(task.getConfigObjectKey(), String.class))
            .bind("outputObjectKey", Parameter.fromOrEmpty(task.getOutputObjectKey(), String.class))
            .bind("sessionId", Parameter.fromOrEmpty(task.getSessionId(), String.class))
            .bind("policyMode", task.getAccessProfile().name())
            .bind("status", task.getStatus().name())
            .bind("currentStage", task.getCurrentStage())
            .bind("progress", task.getProgress())
            .bind("message", task.getMessage())
            .bind("logsJson", gson.toJson(task.getLogs()))
            .bind("stagesJson", gson.toJson(task.getStages()))
            .bind("recoveryPreviousStatus", Parameter.fromOrEmpty(task.getRecoveryPreviousStatus(), String.class))
            .bind("recoveryReason", Parameter.fromOrEmpty(task.getRecoveryReason(), String.class))
            .bind("recoveredAt", Parameter.fromOrEmpty(task.getRecoveredAt(), String.class))
            .bind("updatedAt", task.getUpdatedAt())
            .bind("taskId", task.getId())
            .fetch()
            .rowsUpdated();
    }

    private Mono<Long> insertTask(PlatformTaskRecord task) {
        return databaseClient.sql(
                """
                INSERT INTO control_task_state (
                    task_id,
                    project_name,
                    input_object_key,
                    config_object_key,
                    output_object_key,
                    session_id,
                    policy_mode,
                    status,
                    current_stage,
                    progress,
                    message,
                    logs_json,
                    stages_json,
                    recovery_previous_status,
                    recovery_reason,
                    recovered_at,
                    created_at,
                    updated_at
                ) VALUES (
                    :taskId,
                    :projectName,
                    :inputObjectKey,
                    :configObjectKey,
                    :outputObjectKey,
                    :sessionId,
                    :policyMode,
                    :status,
                    :currentStage,
                    :progress,
                    :message,
                    :logsJson,
                    :stagesJson,
                    :recoveryPreviousStatus,
                    :recoveryReason,
                    :recoveredAt,
                    :createdAt,
                    :updatedAt
                )
                """
            )
            .bind("taskId", task.getId())
            .bind("projectName", task.getProjectName())
            .bind("inputObjectKey", task.getInputObjectKey())
            .bind("configObjectKey", Parameter.fromOrEmpty(task.getConfigObjectKey(), String.class))
            .bind("outputObjectKey", Parameter.fromOrEmpty(task.getOutputObjectKey(), String.class))
            .bind("sessionId", Parameter.fromOrEmpty(task.getSessionId(), String.class))
            .bind("policyMode", task.getAccessProfile().name())
            .bind("status", task.getStatus().name())
            .bind("currentStage", task.getCurrentStage())
            .bind("progress", task.getProgress())
            .bind("message", task.getMessage())
            .bind("logsJson", gson.toJson(task.getLogs()))
            .bind("stagesJson", gson.toJson(task.getStages()))
            .bind("recoveryPreviousStatus", Parameter.fromOrEmpty(task.getRecoveryPreviousStatus(), String.class))
            .bind("recoveryReason", Parameter.fromOrEmpty(task.getRecoveryReason(), String.class))
            .bind("recoveredAt", Parameter.fromOrEmpty(task.getRecoveredAt(), String.class))
            .bind("createdAt", task.getCreatedAt())
            .bind("updatedAt", task.getUpdatedAt())
            .fetch()
            .rowsUpdated();
    }

    @Override
    public boolean recoverInterruptedTask(String taskId, String previousStatus, String reason, String recoveredAt) {
        try {
            Long updated = databaseClient.sql(
                    """
                    UPDATE control_task_state
                    SET status = 'INTERRUPTED',
                        current_stage = 'Interrupted',
                        message = :message,
                        recovery_previous_status = :previousStatus,
                        recovery_reason = :reason,
                        recovered_at = :recoveredAt,
                        updated_at = :recoveredAt
                    WHERE task_id = :taskId
                      AND status IN ('QUEUED', 'STARTING', 'RUNNING')
                    """
                )
                .bind("message", "Task interrupted because control plane restarted before durable orchestration was available")
                .bind("previousStatus", previousStatus)
                .bind("reason", reason)
                .bind("recoveredAt", recoveredAt)
                .bind("taskId", taskId)
                .fetch()
                .rowsUpdated()
                .blockOptional()
                .orElse(0L);
            return updated != null && updated > 0;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @Override
    public List<PersistedTaskState> loadTasks() {
        try {
            List<String> taskIds = databaseClient.sql(
                    """
                    SELECT task_id
                    FROM control_task_state
                    ORDER BY updated_at DESC
                    """
                )
                .map((row, metadata) -> row.get("task_id", String.class))
                .all()
                .collectList()
                .blockOptional()
                .orElseGet(Collections::emptyList);
            List<PersistedTaskState> result = new ArrayList<>();
            for (String taskId : taskIds) {
                try {
                    PersistedTaskState state = findTask(taskId);
                    if (state != null) {
                        result.add(state);
                    }
                } catch (RuntimeException ignored) {
                }
            }
            return result;
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    @Override
    public PersistedTaskState findTask(String taskId) {
        try {
            return databaseClient.sql(
                    """
                    SELECT task_id, project_name, input_object_key, config_object_key, output_object_key,
                           session_id, policy_mode, status, current_stage, progress, message,
                           logs_json, stages_json, recovery_previous_status, recovery_reason,
                           recovered_at, created_at, updated_at
                    FROM control_task_state
                    WHERE task_id = :taskId
                    """
                )
                .bind("taskId", taskId)
                .map((row, metadata) -> new PersistedTaskState(
                    row.get("task_id", String.class),
                    row.get("project_name", String.class),
                    row.get("input_object_key", String.class),
                    row.get("config_object_key", String.class),
                    row.get("output_object_key", String.class),
                    row.get("session_id", String.class),
                    row.get("policy_mode", String.class),
                    row.get("status", String.class),
                    row.get("current_stage", String.class),
                    row.get("progress", Integer.class),
                    row.get("message", String.class),
                    parseStringList(row.get("logs_json", String.class)),
                    parseStages(row.get("stages_json", String.class)),
                    row.get("recovery_previous_status", String.class),
                    row.get("recovery_reason", String.class),
                    row.get("recovered_at", String.class),
                    row.get("created_at", String.class),
                    row.get("updated_at", String.class)
                ))
                .one()
                .blockOptional()
                .orElse(null);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private List<String> parseStringList(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = gson.fromJson(rawJson, stringListType);
            return parsed == null ? List.of() : parsed;
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private List<TaskStageRecord> parseStages(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return List.of();
        }
        try {
            List<TaskStageRecord> parsed = gson.fromJson(rawJson, stageListType);
            return parsed == null ? List.of() : parsed;
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }
}
