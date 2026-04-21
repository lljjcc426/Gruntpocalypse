package net.spartanb312.grunteon.back.bootstrap;

import net.spartanb312.grunteon.obfuscator.web.PlatformTaskService;
import net.spartanb312.grunteon.obfuscator.web.TaskMetadataQuery;
import net.spartanb312.grunteon.obfuscator.web.TaskMetadataStore;
import net.spartanb312.grunteon.obfuscator.web.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ControlPlaneStateBootstrap implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(ControlPlaneStateBootstrap.class);

    private final SessionService sessionService;
    private final PlatformTaskService platformTaskService;
    private final TaskMetadataQuery taskMetadataQuery;
    private final TaskMetadataStore taskMetadataStore;
    private final TaskRecoveryPolicy taskRecoveryPolicy;

    public ControlPlaneStateBootstrap(
        SessionService sessionService,
        PlatformTaskService platformTaskService,
        TaskMetadataQuery taskMetadataQuery,
        TaskMetadataStore taskMetadataStore,
        TaskRecoveryPolicy taskRecoveryPolicy
    ) {
        this.sessionService = sessionService;
        this.platformTaskService = platformTaskService;
        this.taskMetadataQuery = taskMetadataQuery;
        this.taskMetadataStore = taskMetadataStore;
        this.taskRecoveryPolicy = taskRecoveryPolicy;
    }

    @Override
    public void run(ApplicationArguments args) {
        int recoveredTasks = 0;
        for (var task : taskMetadataQuery.loadTasks()) {
            var decision = taskRecoveryPolicy.evaluate(task);
            if (!decision.shouldUpdate()) {
                continue;
            }
            boolean updated = taskMetadataStore.recoverInterruptedTask(
                decision.taskId(),
                decision.previousStatus(),
                decision.reason(),
                decision.recoveredAt()
            );
            if (updated) {
                recoveredTasks++;
            }
        }
        sessionService.preloadPersistedSessions();
        platformTaskService.preloadPersistedTasks();
        logger.info(
            "Control plane state bootstrap restored {} sessions and {} tasks into live cache (recovered {} interrupted tasks)",
            sessionService.listSessionIds().size(),
            platformTaskService.listTaskIds().size(),
            recoveredTasks
        );
    }
}
