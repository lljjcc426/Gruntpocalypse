package net.spartanb312.grunteon.back.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "grunteon.back.runtime")
public class BackRuntimeProperties {

    private String mode = "control";

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode == null ? "control" : mode.trim().toLowerCase();
    }

    public boolean isWorkerMode() {
        return "worker".equals(mode);
    }

    public boolean isControlMode() {
        return !isWorkerMode();
    }
}
