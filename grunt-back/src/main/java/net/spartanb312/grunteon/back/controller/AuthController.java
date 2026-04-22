package net.spartanb312.grunteon.back.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.spartanb312.grunteon.back.support.ApiSupport;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final ReactiveAuthenticationManager authenticationManager;
    private final ServerSecurityContextRepository securityContextRepository;

    public AuthController(
        ReactiveAuthenticationManager authenticationManager,
        ServerSecurityContextRepository securityContextRepository
    ) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping("/login")
    public Mono<Map<String, Object>> login(
        @Validated @RequestBody LoginRequest request,
        ServerWebExchange exchange
    ) {
        UsernamePasswordAuthenticationToken token =
            new UsernamePasswordAuthenticationToken(request.username(), request.password());
        return authenticationManager.authenticate(token)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")))
            .flatMap(authentication ->
                securityContextRepository.save(exchange, new SecurityContextImpl(authentication))
                    .thenReturn(buildAuthResponse(authentication, request.tier()))
            )
            .onErrorMap(
                throwable -> throwable instanceof ResponseStatusException
                    ? throwable
                    : new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")
            );
    }

    @GetMapping("/me")
    public Mono<Map<String, Object>> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
        }
        return Mono.just(buildAuthResponse(authentication, null));
    }

    @PostMapping("/logout")
    public Mono<Map<String, Object>> logout(ServerWebExchange exchange) {
        return exchange.getSession()
            .flatMap(session ->
                session.invalidate()
                    .thenReturn(ApiSupport.ok())
            );
    }

    private Map<String, Object> buildAuthResponse(Authentication authentication, String tier) {
        Map<String, Object> result = new LinkedHashMap<>(ApiSupport.ok());
        String resolvedTier = resolveTier(authentication, tier);
        result.put("username", authentication.getName());
        result.put("roles", authentication.getAuthorities().stream().map(it -> it.getAuthority()).toList());
        result.put("role", highestRole(authentication));
        result.put("tier", resolvedTier);
        result.put("authenticated", true);
        result.put("redirect", "/index.html?tier=" + resolvedTier);
        return result;
    }

    private String resolveTier(Authentication authentication, String requestedTier) {
        String normalizedRequestedTier = normalizeTier(requestedTier);
        Set<String> roles = authentication.getAuthorities().stream()
            .map(it -> it.getAuthority())
            .collect(java.util.stream.Collectors.toSet());
        if (normalizedRequestedTier != null) {
            String requestedRole = roleForTier(normalizedRequestedTier);
            if (requestedRole != null && roles.contains("ROLE_" + requestedRole)) {
                return normalizedRequestedTier;
            }
        }
        return tierForRole(highestRole(authentication));
    }

    private String highestRole(Authentication authentication) {
        Set<String> roles = authentication.getAuthorities().stream()
            .map(it -> it.getAuthority())
            .collect(java.util.stream.Collectors.toSet());
        if (roles.contains("ROLE_SUPER_ADMIN")) return "SUPER_ADMIN";
        if (roles.contains("ROLE_PLATFORM_ADMIN")) return "PLATFORM_ADMIN";
        return "USER";
    }

    private String normalizeTier(String tier) {
        if (tier == null) return null;
        String normalized = tier.trim().toLowerCase();
        return switch (normalized) {
            case "user" -> "user";
            case "platform-admin", "platform_admin", "platformadmin" -> "platform-admin";
            case "super-admin", "super_admin", "superadmin" -> "super-admin";
            default -> null;
        };
    }

    private String roleForTier(String tier) {
        return switch (tier) {
            case "user" -> "USER";
            case "platform-admin" -> "PLATFORM_ADMIN";
            case "super-admin" -> "SUPER_ADMIN";
            default -> null;
        };
    }

    private String tierForRole(String role) {
        return switch (role) {
            case "SUPER_ADMIN" -> "super-admin";
            case "PLATFORM_ADMIN" -> "platform-admin";
            default -> "user";
        };
    }

    public record LoginRequest(
        String username,
        String password,
        String tier
    ) {
    }
}
