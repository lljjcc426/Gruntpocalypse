package net.spartanb312.grunteon.back.worker;

import kotlin.jvm.functions.Function0;
import net.spartanb312.grunteon.obfuscator.web.ObfuscationSession;
import net.spartanb312.grunteon.obfuscator.web.ProjectMeta;
import net.spartanb312.grunteon.obfuscator.web.ProjectSource;
import net.spartanb312.grunteon.obfuscator.web.ProjectTree;
import net.spartanb312.grunteon.obfuscator.web.StartResult;
import net.spartanb312.grunteon.back.worker.protocol.WorkerProjectRequest;
import net.spartanb312.grunteon.back.worker.protocol.WorkerSessionRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "grunteon.back.worker", name = "mode", havingValue = "local", matchIfMissing = true)
public class LocalWorkerGateway implements WorkerGateway {

    private final WorkerExecutionService workerExecutionService;

    public LocalWorkerGateway(
        WorkerExecutionService workerExecutionService
    ) {
        this.workerExecutionService = workerExecutionService;
    }

    @Override
    public StartResult startSession(
        ObfuscationSession session,
        Function0<kotlin.Unit> onFinish,
        Function0<kotlin.Unit> onStart
    ) {
        return workerExecutionService.startSession(WorkerSessionRequest.from(session), onFinish, onStart);
    }

    @Override
    public ProjectMeta projectMeta(ObfuscationSession session, ObfuscationSession.ProjectScope scope) {
        return workerExecutionService.projectMeta(WorkerProjectRequest.from(session, scope));
    }

    @Override
    public ProjectTree projectTree(ObfuscationSession session, ObfuscationSession.ProjectScope scope) {
        return workerExecutionService.projectTree(WorkerProjectRequest.from(session, scope));
    }

    @Override
    public ProjectSource projectSource(
        ObfuscationSession session,
        ObfuscationSession.ProjectScope scope,
        String className
    ) {
        return workerExecutionService.projectSource(WorkerProjectRequest.from(session, scope, className));
    }
}
