package net.spartanb312.grunteon.back;

import static org.assertj.core.api.Assertions.assertThat;

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
        "grunteon.back.auth.users[0].username=test-super-admin",
        "grunteon.back.auth.users[0].password=test-password",
        "grunteon.back.auth.users[0].roles[0]=SUPER_ADMIN"
    }
)
@AutoConfigureWebTestClient
class AuthIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void rejectsProtectedControlPlaneRequestWithoutAuthentication() {
        webTestClient.get()
            .uri("/api/control/tasks")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.message").isEqualTo("Authentication required");
    }

    @Test
    void logsInAndLoadsAuthenticatedProfile() {
        EntityExchangeResult<byte[]> loginResult = webTestClient.post()
            .uri("/api/auth/login")
            .bodyValue(Map.of(
                "username", "test-super-admin",
                "password", "test-password",
                "tier", "super-admin"
            ))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.username").isEqualTo("test-super-admin")
            .jsonPath("$.tier").isEqualTo("super-admin")
            .jsonPath("$.authenticated").isEqualTo(true)
            .returnResult();

        String sessionId = loginResult.getResponseCookies().getFirst("SESSION").getValue();
        assertThat(sessionId).isNotBlank();

        webTestClient.mutate()
            .defaultCookie("SESSION", sessionId)
            .build()
            .get()
            .uri("/api/auth/me")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.username").isEqualTo("test-super-admin")
            .jsonPath("$.authenticated").isEqualTo(true)
            .jsonPath("$.roles[0]").isEqualTo("ROLE_SUPER_ADMIN")
            .jsonPath("$.role").isEqualTo("SUPER_ADMIN")
            .jsonPath("$.tier").isEqualTo("super-admin");
    }

    @Test
    void logoutInvalidatesSessionAndRequiresLoginAgain() {
        EntityExchangeResult<byte[]> loginResult = webTestClient.post()
            .uri("/api/auth/login")
            .bodyValue(Map.of(
                "username", "test-super-admin",
                "password", "test-password",
                "tier", "super-admin"
            ))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .returnResult();

        String sessionId = loginResult.getResponseCookies().getFirst("SESSION").getValue();
        assertThat(sessionId).isNotBlank();

        WebTestClient authenticatedClient = webTestClient.mutate()
            .defaultCookie("SESSION", sessionId)
            .build();

        authenticatedClient.post()
            .uri("/api/auth/logout")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("ok");

        authenticatedClient.get()
            .uri("/api/auth/me")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.message").isEqualTo("Authentication required");
    }
}
