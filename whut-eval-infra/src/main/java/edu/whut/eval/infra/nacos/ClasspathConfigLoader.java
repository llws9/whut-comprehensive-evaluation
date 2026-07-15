package edu.whut.eval.infra.nacos;

import edu.whut.eval.common.log.AppLog;
import edu.whut.eval.infra.nacos.exception.NacosConfigLoadException;
import edu.whut.eval.infra.nacos.model.ConfigResource;
import edu.whut.eval.infra.nacos.model.RawConfigPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

public class ClasspathConfigLoader implements ConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ClasspathConfigLoader.class);
    private static final String SOURCE = "classpath-defaults";
    private static final String CLASSPATH_PREFIX = "config-defaults/";

    @Override
    public Optional<RawConfigPayload> load(ConfigResource resource) {
        String path = CLASSPATH_PREFIX + resource.dataId();
        try {
            ClassPathResource classPathResource = new ClassPathResource(path);
            if (!classPathResource.exists()) {
                AppLog.warn(log, "classpath.config.missing",
                        "dataId", resource.dataId(),
                        "path", path);
                return Optional.empty();
            }
            try (InputStream is = classPathResource.getInputStream()) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                if (content.isBlank()) {
                    AppLog.warn(log, "classpath.config.empty",
                            "dataId", resource.dataId(),
                            "path", path);
                    return Optional.empty();
                }
                AppLog.info(log, "classpath.config.loaded",
                        "dataId", resource.dataId(),
                        "path", path);
                return Optional.of(new RawConfigPayload(resource, content, SOURCE, Instant.now()));
            }
        } catch (IOException exception) {
            AppLog.error(log, "classpath.config.read_failed", exception,
                    "dataId", resource.dataId(),
                    "path", path);
            return Optional.empty();
        }
    }

    @Override
    public RawConfigPayload loadRequired(ConfigResource resource) {
        return load(resource).orElseThrow(() -> new NacosConfigLoadException(resource,
                "required config not found on classpath: " + CLASSPATH_PREFIX + resource.dataId()));
    }
}
