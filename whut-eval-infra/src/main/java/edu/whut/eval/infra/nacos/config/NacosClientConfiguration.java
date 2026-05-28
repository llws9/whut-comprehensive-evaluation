package edu.whut.eval.infra.nacos.config;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import edu.whut.eval.common.exception.ConfigLoadException;
import edu.whut.eval.infra.nacos.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!local")
public class NacosClientConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "infra.nacos.connection")
    public NacosClientProperties nacosClientProperties() {
        return new NacosClientProperties();
    }

    @Bean(destroyMethod = "shutDown")
    public ConfigService nacosConfigService(NacosClientProperties properties) {
        if (properties.getServerAddress() == null || properties.getServerAddress().isBlank()) {
            throw new ConfigLoadException("infra.nacos.connection.server-address must not be blank");
        }
        try {
            return NacosConfigServiceFactory.create(new NacosConnectionOptions(
                    properties.getServerAddress(),
                    properties.getNamespace(),
                    properties.getUsername(),
                    properties.getPassword()
            ));
        } catch (NacosException exception) {
            throw new ConfigLoadException("Failed to create nacos ConfigService", exception);
        }
    }

    @Bean
    public ConfigLoader configLoader(ConfigService configService) {
        return new NacosConfigLoader(configService);
    }

    @Bean
    public ConfigSnapshotRepository configSnapshotRepository() {
        return new InMemoryConfigSnapshotRepository();
    }

    @Bean
    public ConfigSubscriber configSubscriber(ConfigService configService) {
        return new NacosConfigSubscriber(configService);
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
