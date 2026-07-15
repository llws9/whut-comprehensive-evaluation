package edu.whut.eval.infra.nacos.config;

import edu.whut.eval.common.log.AppLog;
import edu.whut.eval.infra.nacos.ClasspathConfigLoader;
import edu.whut.eval.infra.nacos.ConfigBootstrapInitializer;
import edu.whut.eval.infra.nacos.ConfigDefinition;
import edu.whut.eval.infra.nacos.ConfigDefinitionRegistry;
import edu.whut.eval.infra.nacos.ConfigLoader;
import edu.whut.eval.infra.nacos.ConfigSnapshotRepository;
import edu.whut.eval.infra.nacos.ConfigSubscriber;
import edu.whut.eval.infra.nacos.InMemoryConfigSnapshotRepository;
import edu.whut.eval.infra.nacos.NoopConfigSubscriber;
import edu.whut.eval.infra.nacos.StaticConfigDefinitionRegistry;
import edu.whut.eval.infra.nacos.TypedConfigMaterializer;
import edu.whut.eval.infra.nacos.model.ConfigFormat;
import edu.whut.eval.infra.nacos.model.ConfigResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

@Configuration
@Profile("local")
public class LocalConfigConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LocalConfigConfiguration.class);

    @Bean
    @ConfigurationProperties(prefix = "infra.nacos")
    public NacosDefinitionProperties localNacosDefinitionProperties() {
        return new NacosDefinitionProperties();
    }

    @Bean
    public ConfigLoader classpathConfigLoader() {
        AppLog.info(log, "local.config.loader.active", "source", "classpath-defaults");
        return new ClasspathConfigLoader();
    }

    @Bean
    public ConfigSnapshotRepository localConfigSnapshotRepository() {
        return new InMemoryConfigSnapshotRepository();
    }

    @Bean
    public ConfigSubscriber noopConfigSubscriber() {
        return new NoopConfigSubscriber();
    }

    @Bean
    public ConfigDefinitionRegistry localConfigDefinitionRegistry(NacosDefinitionProperties properties) {
        List<ConfigDefinition> definitions = properties.getDefinitions().stream()
                .map(this::toDefinition)
                .toList();
        return new StaticConfigDefinitionRegistry(definitions);
    }

    @Bean
    public ConfigBootstrapInitializer localConfigBootstrapInitializer(ConfigDefinitionRegistry definitionRegistry,
                                                                     ConfigLoader configLoader,
                                                                     ConfigSnapshotRepository snapshotRepository,
                                                                     ConfigSubscriber configSubscriber,
                                                                     TypedConfigMaterializer typedConfigMaterializer) {
        return new ConfigBootstrapInitializer(definitionRegistry, configLoader, snapshotRepository,
                configSubscriber, typedConfigMaterializer);
    }

    private ConfigDefinition toDefinition(NacosDefinitionProperties.Item item) {
        if (item.getName() == null || item.getName().isBlank()) {
            throw new edu.whut.eval.common.exception.ConfigLoadException("infra.nacos.definitions[].name must not be blank");
        }
        if (item.getDataId() == null || item.getDataId().isBlank()) {
            throw new edu.whut.eval.common.exception.ConfigLoadException("infra.nacos.definitions[].data-id must not be blank, name=" + item.getName());
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
