package net.spartanb312.grunteon.back.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "grunteon.back.integration")
public class ControlPlaneIntegrationProperties {

    private boolean temporalEnabled = false;
    private boolean kafkaEnabled = false;
    private boolean redisEnabled = false;
    private boolean minioEnabled = true;
    private boolean otelEnabled = false;
    private long downloadGrantTtlSeconds = 120L;

    public boolean isTemporalEnabled() {
        return temporalEnabled;
    }

    public void setTemporalEnabled(boolean temporalEnabled) {
        this.temporalEnabled = temporalEnabled;
    }

    public boolean isKafkaEnabled() {
        return kafkaEnabled;
    }

    public void setKafkaEnabled(boolean kafkaEnabled) {
        this.kafkaEnabled = kafkaEnabled;
    }

    public boolean isRedisEnabled() {
        return redisEnabled;
    }

    public void setRedisEnabled(boolean redisEnabled) {
        this.redisEnabled = redisEnabled;
    }

    public boolean isMinioEnabled() {
        return minioEnabled;
    }

    public void setMinioEnabled(boolean minioEnabled) {
        this.minioEnabled = minioEnabled;
    }

    public boolean isOtelEnabled() {
        return otelEnabled;
    }

    public void setOtelEnabled(boolean otelEnabled) {
        this.otelEnabled = otelEnabled;
    }

    public long getDownloadGrantTtlSeconds() {
        return downloadGrantTtlSeconds;
    }

    public void setDownloadGrantTtlSeconds(long downloadGrantTtlSeconds) {
        this.downloadGrantTtlSeconds = Math.max(30L, downloadGrantTtlSeconds);
    }
}
