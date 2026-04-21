package net.spartanb312.grunteon.back.controlplane.orchestration;

import kotlin.jvm.functions.Function0;
import net.spartanb312.grunteon.obfuscator.web.ObfuscationSession;
import net.spartanb312.grunteon.obfuscator.web.StartResult;

public interface TaskWorkflowOrchestrator {

    StartResult startSessionWorkflow(
        ObfuscationSession session,
        Function0<kotlin.Unit> onFinish,
        Function0<kotlin.Unit> onStart
    );
}
