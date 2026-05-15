package edu.whut.eval.infra.nacos;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import edu.whut.eval.common.log.AppLog;
import edu.whut.eval.infra.nacos.exception.NacosConfigLoadException;
import edu.whut.eval.infra.nacos.model.ConfigResource;
import edu.whut.eval.infra.nacos.model.RawConfigPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;

public class NacosConfigLoader implements ConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(NacosConfigLoader.class);
    private static final String SOURCE = "nacos";

    private final ConfigService configService;

    public NacosConfigLoader(ConfigService configService) {
        this.configService = configService;
    }

    @Override
    public Optional<RawConfigPayload> load(ConfigResource resource) {
        try {
            String content = configService.getConfig(resource.dataId(), resource.group(), resource.timeoutMs());
            if (content == null || content.isBlank()) {
                AppLog.warn(log, "nacos.config.empty",
                        "dataId", resource.dataId(),
                        "group", resource.group(),
                        "format", resource.format());
                return Optional.empty();
            }
            AppLog.info(log, "nacos.config.loaded",
                    "dataId", resource.dataId(),
                    "group", resource.group(),
                    "format", resource.format());
            return Optional.of(new RawConfigPayload(resource, content, SOURCE, Instant.now()));
        } catch (NacosException exception) {
            throw new NacosConfigLoadException(resource, exception);
        }
    }

    @Override
    public RawConfigPayload loadRequired(ConfigResource resource) {
        return load(resource).orElseThrow(() -> new NacosConfigLoadException(resource, "required config is missing or empty"));
    }
}
