package net.spartanb312.grunteon.back.worker.protocol;

public record WorkerProjectMetaResponse(
    String scope,
    boolean available,
    int classCount
) {
}
