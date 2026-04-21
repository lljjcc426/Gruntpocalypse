package net.spartanb312.grunteon.back.controlplane.cache;

import net.spartanb312.grunteon.obfuscator.web.SessionAccessProfile;

public interface SessionPolicyCache {

    void remember(String sessionId, SessionAccessProfile profile);

    SessionAccessProfile getProfile(String sessionId);
}
