package net.spartanb312.grunteon.back.websocket;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import net.spartanb312.grunteon.obfuscator.web.ObfuscationSession;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

@Component
public class SessionSocketHub {

    private final ConcurrentHashMap<String, Set<WebSocketSession>> consoleSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<WebSocketSession>> progressSessions = new ConcurrentHashMap<>();

    public void registerConsole(String sessionId, WebSocketSession session) {
        consoleSessions.computeIfAbsent(sessionId, key -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void unregisterConsole(String sessionId, WebSocketSession session) {
        unregister(consoleSessions, sessionId, session);
    }

    public void registerProgress(String sessionId, WebSocketSession session) {
        progressSessions.computeIfAbsent(sessionId, key -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void unregisterProgress(String sessionId, WebSocketSession session) {
        unregister(progressSessions, sessionId, session);
    }

    public void attachSessionCallbacks(ObfuscationSession session) {
        session.setOnLogMessage(new Function1<String, Unit>() {
            @Override
            public Unit invoke(String message) {
                broadcastConsole(session.getId(), "{\"type\":\"log\",\"message\":\"" + escapeJson(message) + "\"}");
                return Unit.INSTANCE;
            }
        });
        session.setOnProgressUpdate(new Function1<String, Unit>() {
            @Override
            public Unit invoke(String payload) {
                broadcastProgress(session.getId(), payload);
                return Unit.INSTANCE;
            }
        });
    }

    private void broadcastConsole(String sessionId, String payload) {
        broadcast(consoleSessions.get(sessionId), payload);
    }

    private void broadcastProgress(String sessionId, String payload) {
        broadcast(progressSessions.get(sessionId), payload);
    }

    private void broadcast(Set<WebSocketSession> sessions, String payload) {
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        sessions.removeIf(session -> !session.isOpen());
        for (WebSocketSession session : sessions) {
            session.send(Mono.just(session.textMessage(payload)))
                .onErrorResume(ignored -> Mono.empty())
                .subscribe();
        }
    }

    private void unregister(
        ConcurrentHashMap<String, Set<WebSocketSession>> sessions,
        String sessionId,
        WebSocketSession session
    ) {
        Set<WebSocketSession> set = sessions.get(sessionId);
        if (set == null) {
            return;
        }
        set.remove(session);
        if (set.isEmpty()) {
            sessions.remove(sessionId, set);
        }
    }

    private String escapeJson(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n");
    }
}
