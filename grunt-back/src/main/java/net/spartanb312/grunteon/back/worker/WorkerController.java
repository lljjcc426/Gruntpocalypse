package net.spartanb312.grunteon.back.worker;

import net.spartanb312.grunteon.back.config.WorkerGatewayProperties;
import net.spartanb312.grunteon.back.worker.protocol.WorkerProjectMetaResponse;
import net.spartanb312.grunteon.back.worker.protocol.WorkerProjectRequest;
import net.spartanb312.grunteon.back.worker.protocol.WorkerProjectSourceResponse;
import net.spartanb312.grunteon.back.worker.protocol.WorkerProjectTreeResponse;
import net.spartanb312.grunteon.back.worker.protocol.WorkerSessionRequest;
import net.spartanb312.grunteon.back.worker.protocol.WorkerSessionStateResponse;
import net.spartanb312.grunteon.back.worker.protocol.WorkerStartResponse;
import net.spartanb312.grunteon.obfuscator.web.ProjectMeta;
import net.spartanb312.grunteon.obfuscator.web.ProjectSource;
import net.spartanb312.grunteon.obfuscator.web.ProjectTree;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/worker")
public class WorkerController {

    private static final String WORKER_SECRET_HEADER = "X-Grunteon-Worker-Secret";

    private final WorkerExecutionService workerExecutionService;
    private final WorkerGatewayProperties properties;

    public WorkerController(
        WorkerExecutionService workerExecutionService,
        WorkerGatewayProperties properties
    ) {
        this.workerExecutionService = workerExecutionService;
        this.properties = properties;
    }

    @PostMapping("/sessions/start")
    public WorkerStartResponse startSession(
        @RequestBody WorkerSessionRequest request,
        @RequestHeader(value = WORKER_SECRET_HEADER, required = false) String sharedSecret
    ) {
        verifySecret(sharedSecret);
        return new WorkerStartResponse(workerExecutionService.startSession(request, null, null).name());
    }

    @PostMapping("/sessions/state")
    public WorkerSessionStateResponse sessionState(
        @RequestBody WorkerSessionRequest request,
        @RequestHeader(value = WORKER_SECRET_HEADER, required = false) String sharedSecret
    ) {
        verifySecret(sharedSecret);
        return workerExecutionService.sessionState(request);
    }

    @PostMapping("/project/meta")
    public WorkerProjectMetaResponse projectMeta(
        @RequestBody WorkerProjectRequest request,
        @RequestHeader(value = WORKER_SECRET_HEADER, required = false) String sharedSecret
    ) {
        verifySecret(sharedSecret);
        ProjectMeta response = workerExecutionService.projectMeta(request);
        return new WorkerProjectMetaResponse(response.getScope(), response.getAvailable(), response.getClassCount());
    }

    @PostMapping("/project/tree")
    public WorkerProjectTreeResponse projectTree(
        @RequestBody WorkerProjectRequest request,
        @RequestHeader(value = WORKER_SECRET_HEADER, required = false) String sharedSecret
    ) {
        verifySecret(sharedSecret);
        ProjectTree response = workerExecutionService.projectTree(request);
        return new WorkerProjectTreeResponse(response.getScope(), response.getClasses());
    }

    @PostMapping("/project/source")
    public WorkerProjectSourceResponse projectSource(
        @RequestBody WorkerProjectRequest request,
        @RequestHeader(value = WORKER_SECRET_HEADER, required = false) String sharedSecret
    ) {
        verifySecret(sharedSecret);
        ProjectSource response = workerExecutionService.projectSource(request);
        return new WorkerProjectSourceResponse(
            response.getScope(),
            response.getClassName(),
            response.getLanguage(),
            response.getCode()
        );
    }

    private void verifySecret(String providedSecret) {
        String expectedSecret = properties.getRemote().getSharedSecret();
        if (!expectedSecret.isBlank() && !expectedSecret.equals(providedSecret)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid worker shared secret");
        }
    }
}
