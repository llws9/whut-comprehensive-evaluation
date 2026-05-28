package edu.whut.eval.infra.nacos.config;

import edu.whut.eval.common.exception.ConfigLoadException;
import edu.whut.eval.infra.nacos.ConfigDefinition;
import edu.whut.eval.infra.nacos.ConfigDefinitionRegistry;
import edu.whut.eval.infra.nacos.StaticConfigDefinitionRegistry;
import edu.whut.eval.infra.nacos.model.ConfigFormat;
import edu.whut.eval.infra.nacos.model.ConfigResource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

@Configuration
@Profile("!local")
public class NacosDefinitionConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "infra.nacos")
    public NacosDefinitionProperties nacosDefinitionProperties() {
        return new NacosDefinitionProperties();
    }

    @Bean
    public ConfigDefinitionRegistry configDefinitionRegistry(NacosDefinitionProperties properties) {
        List<ConfigDefinition> definitions = properties.getDefinitions().stream()
                .map(this::toDefinition)
                .toList();
        return new StaticConfigDefinitionRegistry(definitions);
    }

    private ConfigDefinition toDefinition(NacosDefinitionProperties.Item item) {
        if (item.getName() == null || item.getName().isBlank()) {
            throw new ConfigLoadException("infra.nacos.definitions[].name must not be blank");
        }
        if (item.getDataId() == null || item.getDataId().isBlank()) {
            throw new ConfigLoadException("infra.nacos.definitions[].data-id must not be blank, name=" + item.getName());
        }
        ConfigFormat format = item.getFormat() == null ? ConfigFormat.YAML : item.getFormat();
        ConfigResource resource = new ConfigResource(
                item.getDataId(),
                item.getGroup(),
                item.getTimeoutMs() == null ? 3_000L : item.getTimeoutMs(),
                format
        );
        return new ConfigDefinition(item.getName(), resource, item.isRequired(), item.isAutoRefresh());
    }
}
