package edu.whut.eval.domain.config.model;

/**
 * 共享基础配置。
 * 包含应用版本、环境等基础信息。
 */
public class SharedBaseConfig {

    private boolean enabled;
    private String version;
    private String environment;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }
}
