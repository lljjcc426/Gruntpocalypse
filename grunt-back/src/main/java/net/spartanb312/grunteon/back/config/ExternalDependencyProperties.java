package net.spartanb312.grunteon.back.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "grunteon.back.infrastructure")
public class ExternalDependencyProperties {

    private boolean failFast = false;
    private final Minio minio = new Minio();
    private List<String> kafkaTopics = List.of(
        "control.session.create",
        "control.session.obfuscate",
        "control.task.create",
        "control.task.start"
    );

    public boolean isFailFast() {
        return failFast;
    }

    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    public Minio getMinio() {
        return minio;
    }

    public List<String> getKafkaTopics() {
        return kafkaTopics;
    }

    public void setKafkaTopics(List<String> kafkaTopics) {
        this.kafkaTopics = kafkaTopics;
    }

    public static class Minio {

        private String endpoint = "http://minio:9000";
        private String accessKey = "grunteon";
        private String secretKey = "grunteon123";
        private String bucket = "grunteon-artifacts";
        private String region = "us-east-1";

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }
    }
}
