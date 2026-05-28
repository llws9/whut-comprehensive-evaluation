package edu.whut.eval.infra.nacos.config;

import edu.whut.eval.infra.nacos.*;
import edu.whut.eval.domain.config.model.EligibilityRulesConfig;
import edu.whut.eval.domain.config.model.EvaluationItemsConfig;
import edu.whut.eval.domain.config.model.IndexOptionsConfig;
import edu.whut.eval.domain.config.repository.TypedConfigRepository;
import edu.whut.eval.domain.config.model.OssStorageConfig;
import edu.whut.eval.domain.config.model.PlatformRuleConfig;
import edu.whut.eval.domain.config.model.SharedBaseConfig;
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
                new TypedConfigBinding<>("oss-storage-config", OssStorageConfig.class),
                new TypedConfigBinding<>("evaluation-items-config", EvaluationItemsConfig.class),
                new TypedConfigBinding<>("index-options-config", IndexOptionsConfig.class),
                new TypedConfigBinding<>("eligibility-rules-config", EligibilityRulesConfig.class)
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
