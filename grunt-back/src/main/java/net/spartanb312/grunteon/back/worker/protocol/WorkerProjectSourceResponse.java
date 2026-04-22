package net.spartanb312.grunteon.back.worker.protocol;

public record WorkerProjectSourceResponse(
    String scope,
    String className,
    String language,
    String code
) {
}
