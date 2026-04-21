package net.spartanb312.grunteon.back.controlplane.events;

import com.google.gson.Gson;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Primary
@ConditionalOnProperty(prefix = "grunteon.back.integration", name = "kafka-enabled", havingValue = "true")
public class KafkaTaskEventPublisher implements TaskEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Gson gson;

    public KafkaTaskEventPublisher(KafkaTemplate<String, String> kafkaTemplate, Gson gson) {
        this.kafkaTemplate = kafkaTemplate;
        this.gson = gson;
    }

    @Override
    public void publish(String topic, Map<String, Object> event) {
        kafkaTemplate.send(topic, gson.toJson(event));
    }
}
