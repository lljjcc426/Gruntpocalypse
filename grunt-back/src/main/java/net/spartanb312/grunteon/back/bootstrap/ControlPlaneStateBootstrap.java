package net.spartanb312.grunteon.back.bootstrap;

import net.spartanb312.grunteon.obfuscator.web.PlatformTaskService;
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

    public ControlPlaneStateBootstrap(
        SessionService sessionService,
        PlatformTaskService platformTaskService
    ) {
        this.sessionService = sessionService;
        this.platformTaskService = platformTaskService;
    }

    @Override
    public void run(ApplicationArguments args) {
        sessionService.preloadPersistedSessions();
        platformTaskService.preloadPersistedTasks();
        logger.info(
            "Control plane state bootstrap restored {} sessions and {} tasks into live cache",
            sessionService.listSessionIds().size(),
            platformTaskService.listTaskIds().size()
        );
    }
}
