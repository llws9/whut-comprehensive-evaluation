package edu.whut.eval.infra.nacos.config;

import edu.whut.eval.infra.nacos.*;
import edu.whut.eval.infra.nacos.model.typed.OssStorageConfig;
import edu.whut.eval.infra.nacos.model.typed.PlatformRuleConfig;
import edu.whut.eval.infra.nacos.model.typed.SharedBaseConfig;
import edu.whut.eval.infra.nacos.parser.ConfigPayloadParser;
import edu.whut.eval.infra.nacos.parser.YamlConfigPayloadParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class NacosTypedConfigConfiguration {

    @Bean
    public ConfigPayloadParser yamlConfigPayloadParser() {
        return new YamlConfigPayloadParser();
    }

    @Bean
    public TypedConfigBindingRegistry typedConfigBindingRegistry() {
        return new StaticTypedConfigBindingRegistry(List.of(
                new TypedConfigBinding<>("shared-base-config", SharedBaseConfig.class),
                new TypedConfigBinding<>("platform-rule-config", PlatformRuleConfig.class),
                new TypedConfigBinding<>("oss-storage-config", OssStorageConfig.class)
        ));
    }

    @Bean
    public TypedConfigRepository typedConfigRepository() {
        return new InMemoryTypedConfigRepository();
    }

    @Bean
    public TypedConfigMaterializer typedConfigMaterializer(TypedConfigBindingRegistry bindingRegistry,
                                                           TypedConfigRepository typedConfigRepository,
                                                           List<ConfigPayloadParser> parsers) {
        return new TypedConfigMaterializer(bindingRegistry, typedConfigRepository, parsers);
    }
}
