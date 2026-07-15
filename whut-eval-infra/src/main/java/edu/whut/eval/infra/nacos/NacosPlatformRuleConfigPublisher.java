package edu.whut.eval.infra.nacos;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import edu.whut.eval.application.platform.service.ConfigPublishException;
import edu.whut.eval.application.platform.service.PlatformRuleConfigPublisher;
import edu.whut.eval.common.exception.ConfigLoadException;
import edu.whut.eval.domain.config.model.PlatformRuleConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
@Profile("!local")
@ConditionalOnProperty(prefix = "infra.nacos", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NacosPlatformRuleConfigPublisher implements PlatformRuleConfigPublisher {

    private static final String DEFINITION_NAME = "platform-rule-config";

    private final ConfigService configService;
    private final ConfigDefinitionRegistry configDefinitionRegistry;
    private final ObjectMapper objectMapper;

    public NacosPlatformRuleConfigPublisher(ConfigService configService,
                                            ConfigDefinitionRegistry configDefinitionRegistry) {
        this.configService = configService;
        this.configDefinitionRegistry = configDefinitionRegistry;
        this.objectMapper = new ObjectMapper(new YAMLFactory());
    }

    @Override
    public boolean publish(PlatformRuleConfig config, String reason, OffsetDateTime effectiveAt) {
        ConfigDefinition definition = findPlatformRuleDefinition();
        try {
            String content = objectMapper.writeValueAsString(config);
            return configService.publishConfig(
                    definition.resource().dataId(),
                    definition.resource().group(),
                    content,
                    definition.resource().format().name().toLowerCase(java.util.Locale.ROOT)
            );
        } catch (NacosException exception) {
            throw new ConfigPublishException("Failed to publish " + DEFINITION_NAME, exception);
        } catch (Exception exception) {
            throw new ConfigPublishException("Failed to serialize " + DEFINITION_NAME, exception);
        }
    }

    private ConfigDefinition findPlatformRuleDefinition() {
        return configDefinitionRegistry.getDefinitions().stream()
                .filter(definition -> DEFINITION_NAME.equals(definition.name()))
                .findFirst()
                .orElseThrow(() -> new ConfigLoadException("Required config definition not found: " + DEFINITION_NAME));
    }
}
