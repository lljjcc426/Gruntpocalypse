package net.spartanb312.grunteon.back.controlplane.cache;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import net.spartanb312.grunteon.obfuscator.web.SessionAccessProfile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Primary
@ConditionalOnProperty(prefix = "grunteon.back.integration", name = "redis-enabled", havingValue = "true")
public class RedisSessionPolicyCache implements SessionPolicyCache {

    private static final String KEY_PREFIX = "grunteon:session-policy:";
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ConcurrentHashMap<String, SessionAccessProfile> localCache = new ConcurrentHashMap<>();

    public RedisSessionPolicyCache(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void remember(String sessionId, SessionAccessProfile profile) {
        localCache.put(sessionId, profile);
        redisTemplate.opsForValue()
            .set(KEY_PREFIX + sessionId, profile.name(), Duration.ofHours(6))
            .onErrorResume(error -> reactor.core.publisher.Mono.empty())
            .subscribe();
    }

    @Override
    public SessionAccessProfile getProfile(String sessionId) {
        SessionAccessProfile cached = localCache.get(sessionId);
        if (cached != null) {
            return cached;
        }
        return null;
    }
}
