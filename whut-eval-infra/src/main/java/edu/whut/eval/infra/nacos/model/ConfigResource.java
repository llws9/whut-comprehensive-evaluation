package edu.whut.eval.infra.nacos.model;

public record ConfigResource(
        String dataId,
        String group,
        long timeoutMs,
        ConfigFormat format
) {

    private static final String DEFAULT_GROUP = "DEFAULT_GROUP";
    private static final long DEFAULT_TIMEOUT_MS = 3_000L;

    public ConfigResource {
        if (dataId == null || dataId.isBlank()) {
            throw new IllegalArgumentException("dataId must not be blank");
        }
        group = (group == null || group.isBlank()) ? DEFAULT_GROUP : group;
        timeoutMs = timeoutMs <= 0 ? DEFAULT_TIMEOUT_MS : timeoutMs;
        format = format == null ? ConfigFormat.YAML : format;
    }

    public static ConfigResource yaml(String dataId) {
        return new ConfigResource(dataId, DEFAULT_GROUP, DEFAULT_TIMEOUT_MS, ConfigFormat.YAML);
    }

    public static ConfigResource properties(String dataId) {
        return new ConfigResource(dataId, DEFAULT_GROUP, DEFAULT_TIMEOUT_MS, ConfigFormat.PROPERTIES);
    }
}
