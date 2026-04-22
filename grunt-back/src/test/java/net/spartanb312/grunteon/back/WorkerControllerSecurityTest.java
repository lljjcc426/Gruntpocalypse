package net.spartanb312.grunteon.back;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "grunteon.back.worker.remote.shared-secret=test-worker-secret"
    }
)
@AutoConfigureWebTestClient
class WorkerControllerSecurityTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void rejectsWorkerRequestsWithoutSharedSecret() {
        webTestClient.post()
            .uri("/internal/worker/project/meta")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(projectRequest())
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void allowsWorkerRequestsWithSharedSecret() {
        webTestClient.post()
            .uri("/internal/worker/project/meta")
            .header("X-Grunteon-Worker-Secret", "test-worker-secret")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(projectRequest())
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.scope").isEqualTo("input")
            .jsonPath("$.available").isEqualTo(false)
            .jsonPath("$.classCount").isEqualTo(0);
    }

    private Map<String, Object> projectRequest() {
        Map<String, Object> session = new LinkedHashMap<>();
        session.put("sessionId", "session-test");
        session.put("accessProfile", "SECURE");
        session.put("controlPlane", "test-control-plane");
        session.put("workerPlane", "test-worker-plane");
        session.put("configObjectKey", "");
        session.put("configDisplayName", "");
        session.put("inputObjectKey", "");
        session.put("inputDisplayName", "");
        session.put("outputObjectKey", "");
        session.put("libraryObjectRefs", Map.of());
        session.put("assetObjectRefs", Map.of());

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("scope", "INPUT");
        request.put("className", "");
        request.put("session", session);
        return request;
    }
}
