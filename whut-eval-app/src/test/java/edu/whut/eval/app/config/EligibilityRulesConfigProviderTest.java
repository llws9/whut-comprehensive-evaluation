package edu.whut.eval.app.config;

import edu.whut.eval.common.exception.ConfigLoadException;
import edu.whut.eval.infra.nacos.TypedConfigBindingRegistry;
import edu.whut.eval.infra.nacos.TypedConfigMaterializer;
import edu.whut.eval.domain.config.repository.TypedConfigRepository;
import edu.whut.eval.infra.nacos.config.NacosTypedConfigConfiguration;
import edu.whut.eval.infra.nacos.config.EligibilityRulesConfigProvider;
import edu.whut.eval.infra.nacos.model.ConfigResource;
import edu.whut.eval.infra.nacos.model.RawConfigPayload;
import edu.whut.eval.domain.config.model.EligibilityRulesConfig;
import edu.whut.eval.infra.nacos.parser.ConfigPayloadParser;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EligibilityRulesConfigProviderTest {

    private final NacosTypedConfigConfiguration configuration = new NacosTypedConfigConfiguration();

    @Test
    void shouldRegisterEligibilityRulesTypedBinding() {
        TypedConfigBindingRegistry bindingRegistry = configuration.typedConfigBindingRegistry();

        assertThat(bindingRegistry.find(EligibilityRulesConfigProvider.DEFINITION_NAME)).isPresent();
        assertThat(bindingRegistry.find(EligibilityRulesConfigProvider.DEFINITION_NAME).orElseThrow().targetType())
                .isEqualTo(EligibilityRulesConfig.class);
    }

    @Test
    void shouldMaterializeEligibilityRulesConfigFromYamlPayload() {
        TypedConfigBindingRegistry bindingRegistry = configuration.typedConfigBindingRegistry();
        TypedConfigRepository typedConfigRepository = configuration.typedConfigRepository();
        ConfigPayloadParser yamlParser = configuration.yamlConfigPayloadParser();
        TypedConfigMaterializer materializer = configuration.typedConfigMaterializer(
                bindingRegistry,
                typedConfigRepository,
                List.of(yamlParser)
        );
        EligibilityRulesConfigProvider configProvider = new EligibilityRulesConfigProvider(typedConfigRepository);

        materializer.materialize(
                EligibilityRulesConfigProvider.DEFINITION_NAME,
                new RawConfigPayload(
                        ConfigResource.yaml("whut-eval-eligibility-rules.yaml"),
                        "eligibility-rules:\n"
                                + "  rules-001:\n"
                                + "    - ruleId: RULE001\n"
                                + "      ruleType: GPA\n"
                                + "      description: GPA要求规则\n"
                                + "      expression: gpa >= 3.0\n"
                                + "      enabled: true\n",
                        "unit-test",
                        Instant.parse("2026-05-14T09:35:00Z")
                )
        );

        EligibilityRulesConfig config = configProvider.requiredConfig();
        assertThat(config.getEligibilityRules()).containsKey("rules-001");
        List<EligibilityRulesConfig.EligibilityRuleItem> items = config.getEligibilityRules().get("rules-001");
        assertThat(items).hasSize(1);
        EligibilityRulesConfig.EligibilityRuleItem item = items.get(0);
        assertThat(item.getRuleId()).isEqualTo("RULE001");
        assertThat(item.getRuleType()).isEqualTo("GPA");
        assertThat(item.getDescription()).isEqualTo("GPA要求规则");
        assertThat(item.getExpression()).isEqualTo("gpa >= 3.0");
        assertThat(item.isEnabled()).isTrue();
    }

    @Test
    void shouldFailFastWhenEligibilityRulesTypedConfigIsMissing() {
        TypedConfigRepository typedConfigRepository = configuration.typedConfigRepository();
        EligibilityRulesConfigProvider configProvider = new EligibilityRulesConfigProvider(typedConfigRepository);

        assertThat(configProvider.currentConfig()).isEmpty();
        assertThatThrownBy(configProvider::requiredConfig)
                .isInstanceOf(ConfigLoadException.class)
                .hasMessage("Required typed config not found: " + EligibilityRulesConfigProvider.DEFINITION_NAME);
    }
}
