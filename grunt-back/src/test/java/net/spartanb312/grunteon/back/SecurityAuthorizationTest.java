package net.spartanb312.grunteon.back;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "grunteon.back.auth.users[0].username=test-user",
        "grunteon.back.auth.users[0].password=user-password",
        "grunteon.back.auth.users[0].roles[0]=USER",
        "grunteon.back.auth.users[1].username=test-user-two",
        "grunteon.back.auth.users[1].password=user-two-password",
        "grunteon.back.auth.users[1].roles[0]=USER",
        "grunteon.back.auth.users[2].username=test-platform-admin",
        "grunteon.back.auth.users[2].password=platform-password",
        "grunteon.back.auth.users[2].roles[0]=PLATFORM_ADMIN",
        "grunteon.back.auth.users[3].username=test-super-admin",
        "grunteon.back.auth.users[3].password=super-password",
        "grunteon.back.auth.users[3].roles[0]=SUPER_ADMIN"
    }
)
@AutoConfigureWebTestClient
class SecurityAuthorizationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void userRoleCanUseLegacySessionButCannotAccessControlPlane() {
        String sessionId = login("test-user", "user-password", "user");

        webTestClient.mutate()
            .defaultCookie("SESSION", sessionId)
            .build()
            .post()
            .uri("/api/session/create")
            .bodyValue(Map.of("profile", "RESEARCH"))
            .exchange()
            .expectStatus().isOk();

        webTestClient.mutate()
            .defaultCookie("SESSION", sessionId)
            .build()
            .get()
            .uri("/api/control/tasks")
            .exchange()
            .expectStatus().isForbidden();
    }

    @Test
    void platformAdminRoleCanAccessControlPlaneButNotPolicyEndpoint() {
        String sessionId = login("test-platform-admin", "platform-password", "platform-admin");

        webTestClient.mutate()
            .defaultCookie("SESSION", sessionId)
            .build()
            .get()
            .uri("/api/control/tasks")
            .exchange()
            .expectStatus().isOk();

        webTestClient.mutate()
            .defaultCookie("SESSION", sessionId)
            .build()
            .get()
            .uri("/api/control/policy/profile")
            .exchange()
            .expectStatus().isForbidden();
    }

    @Test
    void superAdminRoleCanAccessPolicyEndpoint() {
        String sessionId = login("test-super-admin", "super-password", "super-admin");

        webTestClient.mutate()
            .defaultCookie("SESSION", sessionId)
            .build()
            .get()
            .uri("/api/control/policy/profile")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.defaultProfile").exists();
    }

    @Test
    void regularUsersCanOnlyAccessTheirOwnSessions() {
        String ownerSession = login("test-user", "user-password", "user");
        EntityExchangeResult<byte[]> createSession = webTestClient.mutate()
            .defaultCookie("SESSION", ownerSession)
            .build()
            .post()
            .uri("/api/session/create")
            .bodyValue(Map.of("profile", "RESEARCH"))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.sessionId").exists()
            .returnResult();
        String responseBody = new String(createSession.getResponseBodyContent(), StandardCharsets.UTF_8);
        String createdSessionId = responseBody.replaceAll(".*\"sessionId\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        assertThat(createdSessionId).isNotBlank();

        webTestClient.mutate()
            .defaultCookie("SESSION", login("test-user-two", "user-two-password", "user"))
            .build()
            .get()
            .uri("/api/session/" + createdSessionId + "/status")
            .exchange()
            .expectStatus().isForbidden();

        webTestClient.mutate()
            .defaultCookie("SESSION", login("test-platform-admin", "platform-password", "platform-admin"))
            .build()
            .get()
            .uri("/api/session/" + createdSessionId + "/status")
            .exchange()
            .expectStatus().isOk();
    }

    private String login(String username, String password, String tier) {
        EntityExchangeResult<byte[]> loginResult = webTestClient.post()
            .uri("/api/auth/login")
            .bodyValue(Map.of(
                "username", username,
                "password", password,
                "tier", tier
            ))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .returnResult();
        String sessionId = loginResult.getResponseCookies().getFirst("SESSION").getValue();
        assertThat(sessionId).isNotBlank();
        return sessionId;
    }
}
