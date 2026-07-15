package edu.whut.eval.infra.nacos;

import edu.whut.eval.common.log.AppLog;
import edu.whut.eval.infra.nacos.exception.NacosConfigLoadException;
import edu.whut.eval.infra.nacos.model.ConfigResource;
import edu.whut.eval.infra.nacos.model.RawConfigPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class ClasspathFallbackConfigLoader implements ConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ClasspathFallbackConfigLoader.class);

    private final ConfigLoader primary;
    private final ConfigLoader fallback;

    public ClasspathFallbackConfigLoader(ConfigLoader primary, ConfigLoader fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public Optional<RawConfigPayload> load(ConfigResource resource) {
        try {
            Optional<RawConfigPayload> result = primary.load(resource);
            if (result.isPresent()) {
                return result;
            }
        } catch (RuntimeException exception) {
            AppLog.warn(log, "nacos.config.load_failed_fallback",
                    "dataId", resource.dataId(),
                    "group", resource.group(),
                    "error", exception.getMessage());
        }
        return fallback.load(resource);
    }

    @Override
    public RawConfigPayload loadRequired(ConfigResource resource) {
        return load(resource).orElseThrow(() -> new NacosConfigLoadException(resource,
                "required config missing from both primary loader and classpath fallback: " + resource.dataId()));
    }
}
