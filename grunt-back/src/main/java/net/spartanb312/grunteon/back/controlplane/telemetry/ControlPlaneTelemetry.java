package net.spartanb312.grunteon.back.controlplane.telemetry;

import java.util.Map;

public interface ControlPlaneTelemetry {

    void record(String name, Map<String, Object> attributes);
}
