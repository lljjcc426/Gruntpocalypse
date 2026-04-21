package net.spartanb312.grunteon.back.config;

import net.spartanb312.grunteon.obfuscator.web.SessionAccessProfile;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "grunteon.back.policy")
public class BackPolicyProperties {

    private SessionAccessProfile defaultProfile = SessionAccessProfile.SECURE;
    private SessionAccessProfile uiProfile = SessionAccessProfile.RESEARCH;
    private String controlPlaneName = "spring-control-plane";
    private String workerPlaneName = "local-worker-plane";

    public SessionAccessProfile getDefaultProfile() {
        return defaultProfile;
    }

    public void setDefaultProfile(SessionAccessProfile defaultProfile) {
        this.defaultProfile = defaultProfile;
    }

    public SessionAccessProfile getUiProfile() {
        return uiProfile;
    }

    public void setUiProfile(SessionAccessProfile uiProfile) {
        this.uiProfile = uiProfile;
    }

    public String getControlPlaneName() {
        return controlPlaneName;
    }

    public void setControlPlaneName(String controlPlaneName) {
        this.controlPlaneName = controlPlaneName;
    }

    public String getWorkerPlaneName() {
        return workerPlaneName;
    }

    public void setWorkerPlaneName(String workerPlaneName) {
        this.workerPlaneName = workerPlaneName;
    }
}
