package net.spartanb312.grunteon.back;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class PageAccessIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void loginPageIsPubliclyAccessible() {
        webTestClient.get()
            .uri("/login")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
            .expectBody(String.class)
            .value(body -> org.assertj.core.api.Assertions.assertThat(body).contains("Grunteon"));
    }

    @Test
    void indexPageRedirectsAnonymousUsersToLogin() {
        webTestClient.get()
            .uri("/index.html")
            .exchange()
            .expectStatus().isFound()
            .expectHeader().valueEquals(HttpHeaders.LOCATION, "/login");
    }
}
