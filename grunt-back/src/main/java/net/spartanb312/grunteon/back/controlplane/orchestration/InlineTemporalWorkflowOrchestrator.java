package net.spartanb312.grunteon.back.controlplane.orchestration;

import kotlin.jvm.functions.Function0;
import net.spartanb312.grunteon.back.worker.WorkerGateway;
import net.spartanb312.grunteon.obfuscator.web.ObfuscationSession;
import net.spartanb312.grunteon.obfuscator.web.StartResult;
import org.springframework.stereotype.Service;

@Service
public class InlineTemporalWorkflowOrchestrator implements TaskWorkflowOrchestrator {

    private final WorkerGateway workerGateway;

    public InlineTemporalWorkflowOrchestrator(WorkerGateway workerGateway) {
        this.workerGateway = workerGateway;
    }

    @Override
    public StartResult startSessionWorkflow(
        ObfuscationSession session,
        Function0<kotlin.Unit> onFinish,
        Function0<kotlin.Unit> onStart
    ) {
        return workerGateway.startSession(session, onFinish, onStart);
    }
}
