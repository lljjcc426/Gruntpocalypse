package net.spartanb312.grunteon.back.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "grunteon.back.storage")
public class BackStorageProperties {

    private String sessionBaseDir = ".state/web";
    private String objectBaseDir = ".state/object-store";

    public String getSessionBaseDir() {
        return sessionBaseDir;
    }

    public void setSessionBaseDir(String sessionBaseDir) {
        this.sessionBaseDir = sessionBaseDir;
    }

    public String getObjectBaseDir() {
        return objectBaseDir;
    }

    public void setObjectBaseDir(String objectBaseDir) {
        this.objectBaseDir = objectBaseDir;
    }
}
