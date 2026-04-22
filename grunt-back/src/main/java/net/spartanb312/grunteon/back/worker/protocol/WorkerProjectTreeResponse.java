package net.spartanb312.grunteon.back.worker.protocol;

import java.util.List;

public record WorkerProjectTreeResponse(
    String scope,
    List<String> classes
) {
}
