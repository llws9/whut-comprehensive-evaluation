package edu.whut.eval.app.config;

import edu.whut.eval.common.exception.ConfigLoadException;
import edu.whut.eval.infra.nacos.TypedConfigBindingRegistry;
import edu.whut.eval.infra.nacos.TypedConfigMaterializer;
import edu.whut.eval.domain.config.repository.TypedConfigRepository;
import edu.whut.eval.infra.nacos.config.NacosTypedConfigConfiguration;
import edu.whut.eval.infra.nacos.config.PlatformRuleConfigProvider;
import edu.whut.eval.infra.nacos.model.ConfigResource;
import edu.whut.eval.infra.nacos.model.RawConfigPayload;
import edu.whut.eval.domain.config.model.PlatformRuleConfig;
import edu.whut.eval.infra.nacos.parser.ConfigPayloadParser;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformRuleConfigProviderTest {

    private final NacosTypedConfigConfiguration configuration = new NacosTypedConfigConfiguration();

    @Test
    void shouldRegisterPlatformRuleTypedBinding() {
        TypedConfigBindingRegistry bindingRegistry = configuration.typedConfigBindingRegistry();

        assertThat(bindingRegistry.find(PlatformRuleConfigProvider.DEFINITION_NAME)).isPresent();
        assertThat(bindingRegistry.find(PlatformRuleConfigProvider.DEFINITION_NAME).orElseThrow().targetType())
                .isEqualTo(PlatformRuleConfig.class);
    }

    @Test
    void shouldMaterializePlatformRuleConfigFromYamlPayload() {
        TypedConfigBindingRegistry bindingRegistry = configuration.typedConfigBindingRegistry();
        TypedConfigRepository typedConfigRepository = configuration.typedConfigRepository();
        ConfigPayloadParser yamlParser = configuration.yamlConfigPayloadParser();
        TypedConfigMaterializer materializer = configuration.typedConfigMaterializer(
                bindingRegistry,
                typedConfigRepository,
                List.of(yamlParser)
        );
        PlatformRuleConfigProvider configProvider = new PlatformRuleConfigProvider(typedConfigRepository);

        materializer.materialize(
                PlatformRuleConfigProvider.DEFINITION_NAME,
                new RawConfigPayload(
                        ConfigResource.yaml("whut-eval-platform-rule.yaml"),
                        """
                        studentApplyEnabled: true
                        finalSubmitEnabled: false
                        maxReviewBatchSize: 100
                        studentApplyDeadline: "2026-09-30T23:59:59+08:00"
                        finalSubmitDeadline: "2026-10-15T23:59:59+08:00"
                        """,
                        "unit-test",
                        Instant.parse("2026-05-14T09:35:00Z")
                )
        );

        PlatformRuleConfig config = configProvider.requiredConfig();
        assertThat(config.isStudentApplyEnabled()).isTrue();
        assertThat(config.isFinalSubmitEnabled()).isFalse();
        assertThat(config.getMaxReviewBatchSize()).isEqualTo(100);
        assertThat(config.getStudentApplyDeadline()).isEqualTo("2026-09-30T23:59:59+08:00");
        assertThat(config.getFinalSubmitDeadline()).isEqualTo("2026-10-15T23:59:59+08:00");
    }

    @Test
    void shouldFailFastWhenPlatformRuleTypedConfigIsMissing() {
        TypedConfigRepository typedConfigRepository = configuration.typedConfigRepository();
        PlatformRuleConfigProvider configProvider = new PlatformRuleConfigProvider(typedConfigRepository);

        assertThat(configProvider.currentConfig()).isEmpty();
        assertThatThrownBy(configProvider::requiredConfig)
                .isInstanceOf(ConfigLoadException.class)
                .hasMessage("Required typed config not found: " + PlatformRuleConfigProvider.DEFINITION_NAME);
    }
}
