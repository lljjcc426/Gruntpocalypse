package net.spartanb312.grunteon.back.worker.protocol;

import java.util.List;

public record WorkerSessionStateResponse(
    String sessionId,
    String status,
    String currentStep,
    int progress,
    int totalSteps,
    String errorMessage,
    String outputObjectKey,
    List<String> logs
) {
}
