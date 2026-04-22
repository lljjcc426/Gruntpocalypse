package net.spartanb312.grunteon.back.worker.protocol;

import net.spartanb312.grunteon.obfuscator.web.ObfuscationSession;

public record WorkerProjectRequest(
    WorkerSessionRequest session,
    String scope,
    String className
) {

    public static WorkerProjectRequest from(ObfuscationSession session, ObfuscationSession.ProjectScope scope) {
        return new WorkerProjectRequest(WorkerSessionRequest.from(session), scope.name(), null);
    }

    public static WorkerProjectRequest from(
        ObfuscationSession session,
        ObfuscationSession.ProjectScope scope,
        String className
    ) {
        return new WorkerProjectRequest(WorkerSessionRequest.from(session), scope.name(), className);
    }
}
