package net.spartanb312.grunteon.back.worker;

import kotlin.jvm.functions.Function0;
import net.spartanb312.grunteon.back.config.WorkerGatewayProperties;
import net.spartanb312.grunteon.back.worker.protocol.WorkerProjectMetaResponse;
import net.spartanb312.grunteon.back.worker.protocol.WorkerProjectRequest;
import net.spartanb312.grunteon.back.worker.protocol.WorkerProjectSourceResponse;
import net.spartanb312.grunteon.back.worker.protocol.WorkerProjectTreeResponse;
import net.spartanb312.grunteon.back.worker.protocol.WorkerSessionRequest;
import net.spartanb312.grunteon.back.worker.protocol.WorkerSessionStateResponse;
import net.spartanb312.grunteon.back.worker.protocol.WorkerStartResponse;
import net.spartanb312.grunteon.obfuscator.web.ObfuscationSession;
import net.spartanb312.grunteon.obfuscator.web.ProjectMeta;
import net.spartanb312.grunteon.obfuscator.web.ProjectSource;
import net.spartanb312.grunteon.obfuscator.web.ProjectTree;
import net.spartanb312.grunteon.obfuscator.web.StartResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@ConditionalOnProperty(prefix = "grunteon.back.worker", name = "mode", havingValue = "remote")
public class RemoteWorkerGateway implements WorkerGateway {

    private static final String WORKER_SECRET_HEADER = "X-Grunteon-Worker-Secret";

    private final WebClient webClient;
    private final WorkerGatewayProperties properties;

    public RemoteWorkerGateway(
        WebClient.Builder webClientBuilder,
        WorkerGatewayProperties properties
    ) {
        this.properties = properties;
        this.webClient = webClientBuilder
            .baseUrl(properties.getRemote().getBaseUrl())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    @Override
    public StartResult startSession(
        ObfuscationSession session,
        Function0<kotlin.Unit> onFinish,
        Function0<kotlin.Unit> onStart
    ) {
        WorkerStartResponse response = post(
            "/internal/worker/sessions/start",
            WorkerSessionRequest.from(session),
            WorkerStartResponse.class
        );
        if (onStart != null && response != null && "Started".equalsIgnoreCase(response.result())) {
            onStart.invoke();
        }
        if (response != null && "Started".equalsIgnoreCase(response.result())) {
            startPolling(session, onFinish);
        }
        return response == null ? StartResult.Busy : StartResult.valueOf(response.result());
    }

    @Override
    public ProjectMeta projectMeta(ObfuscationSession session, ObfuscationSession.ProjectScope scope) {
        WorkerProjectMetaResponse response = post(
            "/internal/worker/project/meta",
            WorkerProjectRequest.from(session, scope),
            WorkerProjectMetaResponse.class
        );
        return new ProjectMeta(response.scope(), response.available(), response.classCount());
    }

    @Override
    public ProjectTree projectTree(ObfuscationSession session, ObfuscationSession.ProjectScope scope) {
        WorkerProjectTreeResponse response = post(
            "/internal/worker/project/tree",
            WorkerProjectRequest.from(session, scope),
            WorkerProjectTreeResponse.class
        );
        return new ProjectTree(response.scope(), response.classes());
    }

    @Override
    public ProjectSource projectSource(
        ObfuscationSession session,
        ObfuscationSession.ProjectScope scope,
        String className
    ) {
        WorkerProjectSourceResponse response = post(
            "/internal/worker/project/source",
            WorkerProjectRequest.from(session, scope, className),
            WorkerProjectSourceResponse.class
        );
        return new ProjectSource(response.scope(), response.className(), response.language(), response.code());
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        WebClient.RequestBodySpec request = webClient.post().uri(path);
        if (!properties.getRemote().getSharedSecret().isBlank()) {
            request.header(WORKER_SECRET_HEADER, properties.getRemote().getSharedSecret());
        }
        return request
            .bodyValue(body)
            .retrieve()
            .bodyToMono(responseType)
            .block();
    }

    private void startPolling(ObfuscationSession session, Function0<kotlin.Unit> onFinish) {
        Thread.ofVirtual().name("worker-poll-" + session.getId()).start(() -> {
            while (true) {
                WorkerSessionStateResponse state = post(
                    "/internal/worker/sessions/state",
                    WorkerSessionRequest.from(session),
                    WorkerSessionStateResponse.class
                );
                if (state != null) {
                    session.applyExternalState(
                        state.status(),
                        state.currentStep(),
                        state.progress(),
                        state.totalSteps(),
                        state.errorMessage(),
                        state.outputObjectKey(),
                        state.logs()
                    );
                    if ("COMPLETED".equalsIgnoreCase(state.status()) || "ERROR".equalsIgnoreCase(state.status())) {
                        if (onFinish != null) {
                            onFinish.invoke();
                        }
                        return;
                    }
                }
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });
    }
}
