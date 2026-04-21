package net.spartanb312.grunteon.back.controlplane.events;

import java.util.Map;

public interface TaskEventPublisher {

    void publish(String topic, Map<String, Object> event);
}
