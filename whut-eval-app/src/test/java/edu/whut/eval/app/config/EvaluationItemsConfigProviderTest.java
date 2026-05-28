package edu.whut.eval.app.config;

import edu.whut.eval.common.exception.ConfigLoadException;
import edu.whut.eval.infra.nacos.TypedConfigBindingRegistry;
import edu.whut.eval.infra.nacos.TypedConfigMaterializer;
import edu.whut.eval.domain.config.repository.TypedConfigRepository;
import edu.whut.eval.infra.nacos.config.NacosTypedConfigConfiguration;
import edu.whut.eval.infra.nacos.config.EvaluationItemsConfigProvider;
import edu.whut.eval.infra.nacos.model.ConfigResource;
import edu.whut.eval.infra.nacos.model.RawConfigPayload;
import edu.whut.eval.domain.config.model.EvaluationItemsConfig;
import edu.whut.eval.infra.nacos.parser.ConfigPayloadParser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationItemsConfigProviderTest {

    private final NacosTypedConfigConfiguration configuration = new NacosTypedConfigConfiguration();

    @Test
    void shouldRegisterEvaluationItemsTypedBinding() {
        TypedConfigBindingRegistry bindingRegistry = configuration.typedConfigBindingRegistry();

        assertThat(bindingRegistry.find(EvaluationItemsConfigProvider.DEFINITION_NAME)).isPresent();
        assertThat(bindingRegistry.find(EvaluationItemsConfigProvider.DEFINITION_NAME).orElseThrow().targetType())
                .isEqualTo(EvaluationItemsConfig.class);
    }

    @Test
    void shouldMaterializeEvaluationItemsConfigFromYamlPayload() {
        TypedConfigBindingRegistry bindingRegistry = configuration.typedConfigBindingRegistry();
        TypedConfigRepository typedConfigRepository = configuration.typedConfigRepository();
        ConfigPayloadParser yamlParser = configuration.yamlConfigPayloadParser();
        TypedConfigMaterializer materializer = configuration.typedConfigMaterializer(
                bindingRegistry,
                typedConfigRepository,
                List.of(yamlParser)
        );
        EvaluationItemsConfigProvider configProvider = new EvaluationItemsConfigProvider(typedConfigRepository);

        materializer.materialize(
                EvaluationItemsConfigProvider.DEFINITION_NAME,
                new RawConfigPayload(
                        ConfigResource.yaml("whut-eval-evaluation-items.yaml"),
                        "evaluation-items:\n"
                                + "  category-001:\n"
                                + "    - itemCode: ITEM001\n"
                                + "      itemName: 测评项目1\n"
                                + "      categoryCode: category-001\n"
                                + "      categoryName: 类别1\n"
                                + "      description: 描述\n"
                                + "      maxPoints: 10.0\n"
                                + "      enabled: true\n"
                                + "      sortOrder: 1\n",
                        "unit-test",
                        Instant.parse("2026-05-14T09:35:00Z")
                )
        );

        EvaluationItemsConfig config = configProvider.requiredConfig();
        assertThat(config.getEvaluationItems()).containsKey("category-001");
        List<EvaluationItemsConfig.EvaluationItem> items = config.getEvaluationItems().get("category-001");
        assertThat(items).hasSize(1);
        EvaluationItemsConfig.EvaluationItem item = items.get(0);
        assertThat(item.getItemCode()).isEqualTo("ITEM001");
        assertThat(item.getItemName()).isEqualTo("测评项目1");
        assertThat(item.getCategoryCode()).isEqualTo("category-001");
        assertThat(item.getCategoryName()).isEqualTo("类别1");
        assertThat(item.getMaxPoints()).isEqualTo(new BigDecimal("10.0"));
        assertThat(item.isEnabled()).isTrue();
        assertThat(item.getSortOrder()).isEqualTo(1);
    }

    @Test
    void shouldFailFastWhenEvaluationItemsTypedConfigIsMissing() {
        TypedConfigRepository typedConfigRepository = configuration.typedConfigRepository();
        EvaluationItemsConfigProvider configProvider = new EvaluationItemsConfigProvider(typedConfigRepository);

        assertThat(configProvider.currentConfig()).isEmpty();
        assertThatThrownBy(configProvider::requiredConfig)
                .isInstanceOf(ConfigLoadException.class)
                .hasMessage("Required typed config not found: " + EvaluationItemsConfigProvider.DEFINITION_NAME);
    }
}
