package net.spartanb312.grunteon.back.config;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

@Component
public class ControlPlaneDependencyProbe implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(ControlPlaneDependencyProbe.class);

    private final ControlPlaneIntegrationProperties integrationProperties;
    private final ExternalDependencyProperties externalDependencyProperties;
    private final DatabaseClient databaseClient;
    private final Optional<ReactiveStringRedisTemplate> redisTemplate;
    private final Optional<org.springframework.kafka.core.KafkaAdmin> kafkaAdmin;
    private final Optional<MinioClient> minioClient;

    public ControlPlaneDependencyProbe(
        ControlPlaneIntegrationProperties integrationProperties,
        ExternalDependencyProperties externalDependencyProperties,
        DatabaseClient databaseClient,
        Optional<ReactiveStringRedisTemplate> redisTemplate,
        Optional<org.springframework.kafka.core.KafkaAdmin> kafkaAdmin,
        Optional<MinioClient> minioClient
    ) {
        this.integrationProperties = integrationProperties;
        this.externalDependencyProperties = externalDependencyProperties;
        this.databaseClient = databaseClient;
        this.redisTemplate = redisTemplate;
        this.kafkaAdmin = kafkaAdmin;
        this.minioClient = minioClient;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        probePostgres();
        if (integrationProperties.isRedisEnabled()) {
            probeRedis();
        }
        if (integrationProperties.isKafkaEnabled()) {
            probeKafka();
        }
        if (integrationProperties.isMinioEnabled()) {
            probeMinio();
        }
    }

    private void probePostgres() {
        try {
            Integer result = databaseClient.sql("SELECT 1")
                .map((row, metadata) -> row.get(0, Integer.class))
                .one()
                .block(Duration.ofSeconds(5));
            if (result == null || result != 1) {
                throw new IllegalStateException("Unexpected Postgres probe result");
            }
            logger.info("Control plane dependency probe: PostgreSQL reachable");
        } catch (Exception exception) {
            failOrWarn("PostgreSQL", exception);
        }
    }

    private void probeRedis() {
        ReactiveStringRedisTemplate template = redisTemplate.orElseThrow(() -> new IllegalStateException("Reactive Redis template not configured"));
        String key = "grunteon:probe:" + UUID.randomUUID();
        try {
            String value = template.opsForValue()
                .set(key, "ok", Duration.ofSeconds(30))
                .flatMap(ignored -> template.opsForValue().get(key))
                .block(Duration.ofSeconds(5));
            template.delete(key).block(Duration.ofSeconds(5));
            if (!"ok".equals(value)) {
                throw new IllegalStateException("Unexpected Redis probe value");
            }
            logger.info("Control plane dependency probe: Redis reachable");
        } catch (Exception exception) {
            failOrWarn("Redis", exception);
        }
    }

    private void probeKafka() {
        org.springframework.kafka.core.KafkaAdmin admin = kafkaAdmin.orElseThrow(() -> new IllegalStateException("Kafka admin not configured"));
        try (AdminClient client = AdminClient.create(admin.getConfigurationProperties())) {
            client.listTopics().names().get(5, TimeUnit.SECONDS);
            logger.info("Control plane dependency probe: Kafka reachable");
        } catch (Exception exception) {
            failOrWarn("Kafka", exception);
        }
    }

    private void probeMinio() {
        MinioClient client = minioClient.orElseThrow(() -> new IllegalStateException("MinIO client not configured"));
        try {
            boolean exists = client.bucketExists(
                BucketExistsArgs.builder().bucket(externalDependencyProperties.getMinio().getBucket()).build()
            );
            if (!exists) {
                throw new IllegalStateException("Configured MinIO bucket does not exist");
            }
            logger.info("Control plane dependency probe: MinIO reachable");
        } catch (Exception exception) {
            failOrWarn("MinIO", exception);
        }
    }

    private void failOrWarn(String dependency, Exception exception) {
        if (externalDependencyProperties.isFailFast()) {
            throw new IllegalStateException("Control plane dependency probe failed for " + dependency, exception);
        }
        logger.warn("Control plane dependency probe failed for {}: {}", dependency, exception.getMessage());
    }
}
