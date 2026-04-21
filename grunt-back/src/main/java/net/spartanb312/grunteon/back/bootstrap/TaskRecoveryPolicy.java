package net.spartanb312.grunteon.back.bootstrap;

import java.time.Instant;
import java.util.Set;
import net.spartanb312.grunteon.obfuscator.web.PersistedTaskState;
import org.springframework.stereotype.Component;

@Component
public class TaskRecoveryPolicy {

    public static final String RECOVERY_REASON = "CONTROL_PLANE_RESTARTED";
    private static final Set<String> INTERRUPTIBLE = Set.of("QUEUED", "STARTING", "RUNNING");
    private static final Set<String> TERMINAL = Set.of("COMPLETED", "FAILED", "CANCELLED", "INTERRUPTED");

    public RecoveryDecision evaluate(PersistedTaskState task) {
        String status = task.getStatus();
        if (status == null || status.isBlank()) {
            return RecoveryDecision.noop(task.getTaskId(), status);
        }
        if ("CREATED".equals(status) || TERMINAL.contains(status)) {
            return RecoveryDecision.noop(task.getTaskId(), status);
        }
        if (INTERRUPTIBLE.contains(status)) {
            return RecoveryDecision.interrupt(task.getTaskId(), status, Instant.now().toString());
        }
        return RecoveryDecision.noop(task.getTaskId(), status);
    }

    public record RecoveryDecision(
        String taskId,
        String previousStatus,
        String targetStatus,
        String reason,
        String recoveredAt,
        boolean shouldUpdate
    ) {
        static RecoveryDecision noop(String taskId, String previousStatus) {
            return new RecoveryDecision(taskId, previousStatus, previousStatus, null, null, false);
        }

        static RecoveryDecision interrupt(String taskId, String previousStatus, String recoveredAt) {
            return new RecoveryDecision(taskId, previousStatus, "INTERRUPTED", RECOVERY_REASON, recoveredAt, true);
        }
    }
}
