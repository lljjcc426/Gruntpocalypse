package net.spartanb312.grunteon.back.config;

import java.util.LinkedHashMap;
import java.util.Map;
import net.spartanb312.grunteon.back.websocket.ConsoleWebSocketHandler;
import net.spartanb312.grunteon.back.websocket.ProgressWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

@Configuration(proxyBeanMethods = false)
public class WebSocketConfig {

    @Bean
    public HandlerMapping webSocketMapping(
        ConsoleWebSocketHandler consoleWebSocketHandler,
        ProgressWebSocketHandler progressWebSocketHandler
    ) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("/ws/console", consoleWebSocketHandler);
        map.put("/ws/progress", progressWebSocketHandler);
        map.put("/ws/control/console", consoleWebSocketHandler);
        map.put("/ws/control/progress", progressWebSocketHandler);

        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setOrder(Ordered.HIGHEST_PRECEDENCE);
        mapping.setUrlMap(map);
        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
