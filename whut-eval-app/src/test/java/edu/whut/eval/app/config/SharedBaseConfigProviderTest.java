package edu.whut.eval.app.config;

import edu.whut.eval.common.exception.ConfigLoadException;
import edu.whut.eval.infra.nacos.TypedConfigBindingRegistry;
import edu.whut.eval.infra.nacos.TypedConfigMaterializer;
import edu.whut.eval.domain.config.repository.TypedConfigRepository;
import edu.whut.eval.infra.nacos.config.NacosTypedConfigConfiguration;
import edu.whut.eval.infra.nacos.config.SharedBaseConfigProvider;
import edu.whut.eval.infra.nacos.model.ConfigResource;
import edu.whut.eval.infra.nacos.model.RawConfigPayload;
import edu.whut.eval.infra.nacos.model.typed.SharedBaseConfig;
import edu.whut.eval.infra.nacos.parser.ConfigPayloadParser;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SharedBaseConfigProviderTest {

    private final NacosTypedConfigConfiguration configuration = new NacosTypedConfigConfiguration();

    @Test
    void shouldRegisterSharedBaseTypedBinding() {
        TypedConfigBindingRegistry bindingRegistry = configuration.typedConfigBindingRegistry();

        assertThat(bindingRegistry.find(SharedBaseConfigProvider.DEFINITION_NAME)).isPresent();
        assertThat(bindingRegistry.find(SharedBaseConfigProvider.DEFINITION_NAME).orElseThrow().targetType())
                .isEqualTo(SharedBaseConfig.class);
    }

    @Test
    void shouldMaterializeSharedBaseConfigFromYamlPayload() {
        TypedConfigBindingRegistry bindingRegistry = configuration.typedConfigBindingRegistry();
        TypedConfigRepository typedConfigRepository = configuration.typedConfigRepository();
        ConfigPayloadParser yamlParser = configuration.yamlConfigPayloadParser();
        TypedConfigMaterializer materializer = configuration.typedConfigMaterializer(
                bindingRegistry,
                typedConfigRepository,
                List.of(yamlParser)
        );
        SharedBaseConfigProvider configProvider = new SharedBaseConfigProvider(typedConfigRepository);

        materializer.materialize(
                SharedBaseConfigProvider.DEFINITION_NAME,
                new RawConfigPayload(
                        ConfigResource.yaml("whut-eval-shared-base.yaml"),
                        "enabled: true\n"
                                + "version: 1.0.0\n"
                                + "environment: dev\n",
                        "unit-test",
                        Instant.parse("2026-05-14T09:35:00Z")
                )
        );

        SharedBaseConfig config = configProvider.requiredConfig();
        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getVersion()).isEqualTo("1.0.0");
        assertThat(config.getEnvironment()).isEqualTo("dev");
    }

    @Test
    void shouldFailFastWhenSharedBaseTypedConfigIsMissing() {
        TypedConfigRepository typedConfigRepository = configuration.typedConfigRepository();
        SharedBaseConfigProvider configProvider = new SharedBaseConfigProvider(typedConfigRepository);

        assertThat(configProvider.currentConfig()).isEmpty();
        assertThatThrownBy(configProvider::requiredConfig)
                .isInstanceOf(ConfigLoadException.class)
                .hasMessage("Required typed config not found: " + SharedBaseConfigProvider.DEFINITION_NAME);
    }
}
