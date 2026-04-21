package net.spartanb312.grunteon.back.websocket;

import net.spartanb312.grunteon.obfuscator.web.ObfuscationSession;
import net.spartanb312.grunteon.obfuscator.web.SessionService;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Component
public class ConsoleWebSocketHandler implements WebSocketHandler {

    private final SessionService sessionService;
    private final SessionSocketHub sessionSocketHub;

    public ConsoleWebSocketHandler(SessionService sessionService, SessionSocketHub sessionSocketHub) {
        this.sessionService = sessionService;
        this.sessionSocketHub = sessionSocketHub;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = extractSessionId(session);
        ObfuscationSession obfuscationSession = sessionService.getSession(sessionId);
        if (obfuscationSession == null) {
            return session.close();
        }

        sessionSocketHub.registerConsole(sessionId, session);
        obfuscationSession.getConsoleLogs().forEach(line ->
            session.send(Mono.just(session.textMessage("{\"type\":\"log\",\"message\":\"" + escapeJson(line) + "\"}")))
                .onErrorResume(ignored -> Mono.empty())
                .subscribe()
        );

        return session.receive()
            .then()
            .doFinally(signalType -> sessionSocketHub.unregisterConsole(sessionId, session));
    }

    private String extractSessionId(WebSocketSession session) {
        String sessionId = session.getHandshakeInfo().getUri().getQuery();
        if (sessionId == null) {
            throw new ServerWebInputException("Missing sessionId");
        }
        for (String part : sessionId.split("&")) {
            if (part.startsWith("sessionId=")) {
                return part.substring("sessionId=".length());
            }
        }
        throw new ServerWebInputException("Missing sessionId");
    }

    private String escapeJson(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n");
    }
}
