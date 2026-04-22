package net.spartanb312.grunteon.back.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "grunteon.back.worker")
public class WorkerGatewayProperties {

    private String mode = "local";
    private final Remote remote = new Remote();

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode == null ? "local" : mode.trim().toLowerCase();
    }

    public Remote getRemote() {
        return remote;
    }

    public static class Remote {
        private String baseUrl = "http://127.0.0.1:8080";
        private String sharedSecret = "";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getSharedSecret() {
            return sharedSecret;
        }

        public void setSharedSecret(String sharedSecret) {
            this.sharedSecret = sharedSecret == null ? "" : sharedSecret;
        }
    }
}
