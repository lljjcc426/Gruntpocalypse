package net.spartanb312.grunteon.back.config;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public ServerSecurityContextRepository serverSecurityContextRepository() {
        return new WebSessionServerSecurityContextRepository();
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(
        ServerHttpSecurity http,
        ServerSecurityContextRepository securityContextRepository,
        BackRuntimeProperties runtimeProperties
    ) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .logout(ServerHttpSecurity.LogoutSpec::disable)
            .securityContextRepository(securityContextRepository)
            .authorizeExchange(exchanges -> {
                if (runtimeProperties.isWorkerMode()) {
                    exchanges
                        .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                        .pathMatchers("/internal/worker/**").permitAll()
                        .anyExchange().denyAll();
                } else {
                    exchanges
                        .pathMatchers("/", "/login", "/login.html", "/index.html").permitAll()
                        .pathMatchers("/css/**", "/fonts/**", "/schema/**", "/js/**", "/favicon.ico").permitAll()
                        .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                        .pathMatchers("/api/auth/**").permitAll()
                        .pathMatchers("/internal/worker/**").permitAll()
                        .pathMatchers("/api/control/policy/**").hasRole("SUPER_ADMIN")
                        .pathMatchers("/api/control/**", "/api/v1/**").hasAnyRole("PLATFORM_ADMIN", "SUPER_ADMIN")
                        .pathMatchers("/api/session/**", "/ws/**").permitAll()
                        .anyExchange().permitAll();
                }
            })
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((exchange, ex) -> handleUnauthenticated(exchange, runtimeProperties))
                .accessDeniedHandler((exchange, ex) -> writeJson(exchange, HttpStatus.FORBIDDEN, "{\"status\":\"error\",\"message\":\"Access denied\"}"))
            )
            .build();
    }

    private Mono<Void> handleUnauthenticated(ServerWebExchange exchange, BackRuntimeProperties runtimeProperties) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (runtimeProperties.isWorkerMode()) {
            if (path.startsWith("/api/") || path.startsWith("/ws/") || path.startsWith("/internal/")) {
                return writeJson(
                    exchange,
                    HttpStatus.FORBIDDEN,
                    "{\"status\":\"error\",\"message\":\"Worker runtime only exposes internal worker APIs\"}"
                );
            }
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }
        if (path.startsWith("/api/control/") || path.startsWith("/api/v1/")) {
            return writeJson(
                exchange,
                HttpStatus.UNAUTHORIZED,
                "{\"status\":\"error\",\"message\":\"Authentication required\"}"
            );
        }
        if (path.startsWith("/api/") || path.startsWith("/ws/")) {
            return writeJson(
                exchange,
                HttpStatus.UNAUTHORIZED,
                "{\"status\":\"error\",\"message\":\"Authentication required\"}"
            );
        }
        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(URI.create("/login"));
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> writeJson(ServerWebExchange exchange, HttpStatus status, String body) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }
}
