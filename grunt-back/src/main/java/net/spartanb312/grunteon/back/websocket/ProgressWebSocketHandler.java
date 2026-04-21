package net.spartanb312.grunteon.back.websocket;

import net.spartanb312.grunteon.obfuscator.web.ObfuscationSession;
import net.spartanb312.grunteon.obfuscator.web.SessionService;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Component
public class ProgressWebSocketHandler implements WebSocketHandler {

    private final SessionService sessionService;
    private final SessionSocketHub sessionSocketHub;

    public ProgressWebSocketHandler(SessionService sessionService, SessionSocketHub sessionSocketHub) {
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

        sessionSocketHub.registerProgress(sessionId, session);
        if (!obfuscationSession.getCurrentStep().isBlank() || obfuscationSession.getProgress() > 0) {
            String payload = "{\"step\":\"" + escapeJson(obfuscationSession.getCurrentStep()) + "\","
                + "\"current\":0,"
                + "\"total\":" + obfuscationSession.getTotalSteps() + ","
                + "\"progress\":" + obfuscationSession.getProgress() + ","
                + "\"status\":\"" + obfuscationSession.getStatus().name() + "\"}";
            session.send(Mono.just(session.textMessage(payload)))
                .onErrorResume(ignored -> Mono.empty())
                .subscribe();
        }

        return session.receive()
            .then()
            .doFinally(signalType -> sessionSocketHub.unregisterProgress(sessionId, session));
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
