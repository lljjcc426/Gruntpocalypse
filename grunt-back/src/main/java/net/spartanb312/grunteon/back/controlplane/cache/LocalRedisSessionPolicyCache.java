package net.spartanb312.grunteon.back.controlplane.cache;

import java.util.concurrent.ConcurrentHashMap;
import net.spartanb312.grunteon.obfuscator.web.SessionAccessProfile;
import org.springframework.stereotype.Service;

@Service
public class LocalRedisSessionPolicyCache implements SessionPolicyCache {

    private final ConcurrentHashMap<String, SessionAccessProfile> cache = new ConcurrentHashMap<>();

    @Override
    public void remember(String sessionId, SessionAccessProfile profile) {
        cache.put(sessionId, profile);
    }

    @Override
    public SessionAccessProfile getProfile(String sessionId) {
        return cache.get(sessionId);
    }
}
