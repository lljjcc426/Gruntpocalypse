package net.spartanb312.grunteon.back.controlplane.telemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OpenTelemetryControlPlaneTelemetry implements ControlPlaneTelemetry {

    private final Tracer tracer = GlobalOpenTelemetry.getTracer("grunteon-control-plane");

    @Override
    public void record(String name, Map<String, Object> attributes) {
        Span span = tracer.spanBuilder(name).startSpan();
        try {
            attributes.forEach((key, value) -> span.setAttribute(AttributeKey.stringKey(key), String.valueOf(value)));
        } finally {
            span.end();
        }
    }
}
