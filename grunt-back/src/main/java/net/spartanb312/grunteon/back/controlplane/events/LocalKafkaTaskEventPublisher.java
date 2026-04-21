package net.spartanb312.grunteon.back.controlplane.events;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LocalKafkaTaskEventPublisher implements TaskEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(LocalKafkaTaskEventPublisher.class);

    @Override
    public void publish(String topic, Map<String, Object> event) {
        logger.debug("control-plane event [{}]: {}", topic, event);
    }
}
