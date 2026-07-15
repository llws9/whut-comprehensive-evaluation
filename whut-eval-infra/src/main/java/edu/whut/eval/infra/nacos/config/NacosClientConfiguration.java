package edu.whut.eval.infra.nacos.config;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import edu.whut.eval.common.exception.ConfigLoadException;
import edu.whut.eval.common.log.AppLog;
import edu.whut.eval.infra.nacos.ClasspathConfigLoader;
import edu.whut.eval.infra.nacos.ClasspathFallbackConfigLoader;
import edu.whut.eval.infra.nacos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!local")
public class NacosClientConfiguration {

    private static final Logger log = LoggerFactory.getLogger(NacosClientConfiguration.class);

    @Bean
    @ConfigurationProperties(prefix = "infra.nacos.connection")
    public NacosClientProperties nacosClientProperties() {
        return new NacosClientProperties();
    }

    @Bean(destroyMethod = "shutDown")
    @ConditionalOnProperty(prefix = "infra.nacos", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ConfigService nacosConfigService(NacosClientProperties properties) {
        if (properties.getServerAddress() == null || properties.getServerAddress().isBlank()) {
            throw new ConfigLoadException("infra.nacos.connection.server-address must not be blank (set infra.nacos.enabled=false to disable Nacos)");
        }
        try {
            ConfigService service = NacosConfigServiceFactory.create(new NacosConnectionOptions(
                    properties.getServerAddress(),
                    properties.getNamespace(),
                    properties.getUsername(),
                    properties.getPassword()
            ));
            AppLog.info(log, "nacos.client.created", "serverAddress", properties.getServerAddress());
            return service;
        } catch (NacosException exception) {
            throw new ConfigLoadException("Failed to create nacos ConfigService (set infra.nacos.enabled=false to disable Nacos)", exception);
        }
    }

    @Bean
    public ClasspathConfigLoader classpathFallbackLoader() {
        return new ClasspathConfigLoader();
    }

    @Bean
    @ConditionalOnProperty(prefix = "infra.nacos", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ConfigLoader nacosConfigLoader(ConfigService configService, ClasspathConfigLoader fallback) {
        return new ClasspathFallbackConfigLoader(new NacosConfigLoader(configService), fallback);
    }

    @Bean
    @ConditionalOnProperty(prefix = "infra.nacos", name = "enabled", havingValue = "false")
    public ConfigLoader classpathOnlyConfigLoader(ClasspathConfigLoader fallback) {
        AppLog.info(log, "nacos.disabled", "message", "Nacos disabled via infra.nacos.enabled=false; using classpath defaults");
        return fallback;
    }

    @Bean
    public ConfigSnapshotRepository configSnapshotRepository() {
        return new InMemoryConfigSnapshotRepository();
    }

    @Bean
    @ConditionalOnProperty(prefix = "infra.nacos", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ConfigSubscriber nacosConfigSubscriber(ConfigService configService) {
        return new NacosConfigSubscriber(configService);
    }

    @Bean
    @ConditionalOnProperty(prefix = "infra.nacos", name = "enabled", havingValue = "false")
    public ConfigSubscriber noopConfigSubscriberWhenDisabled() {
        return new NoopConfigSubscriber();
    }

    @Bean
    public ConfigBootstrapInitializer configBootstrapInitializer(ConfigDefinitionRegistry definitionRegistry,
                                                                ConfigLoader configLoader,
                                                                ConfigSnapshotRepository snapshotRepository,
                                                                ConfigSubscriber configSubscriber,
                                                                TypedConfigMaterializer typedConfigMaterializer) {
        return new ConfigBootstrapInitializer(definitionRegistry, configLoader, snapshotRepository, configSubscriber,
                typedConfigMaterializer);
    }
}
