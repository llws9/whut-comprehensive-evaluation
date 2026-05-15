package edu.whut.eval.infra.nacos;

import edu.whut.eval.infra.nacos.model.ConfigResource;

public record ConfigDefinition(
        String name,
        ConfigResource resource,
        boolean required,
        boolean autoRefresh
) {

    public ConfigDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("config definition name must not be blank");
        }
        if (resource == null) {
            throw new IllegalArgumentException("config resource must not be null");
        }
    }

    public static ConfigDefinition required(String name, ConfigResource resource) {
        return new ConfigDefinition(name, resource, true, true);
    }
}
