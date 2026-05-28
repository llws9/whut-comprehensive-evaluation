package edu.whut.eval.app.config;

import edu.whut.eval.common.exception.ConfigLoadException;
import edu.whut.eval.infra.nacos.TypedConfigBindingRegistry;
import edu.whut.eval.infra.nacos.TypedConfigMaterializer;
import edu.whut.eval.domain.config.repository.TypedConfigRepository;
import edu.whut.eval.infra.nacos.config.NacosTypedConfigConfiguration;
import edu.whut.eval.infra.nacos.config.IndexOptionsConfigProvider;
import edu.whut.eval.infra.nacos.model.ConfigResource;
import edu.whut.eval.infra.nacos.model.RawConfigPayload;
import edu.whut.eval.domain.config.model.IndexOptionsConfig;
import edu.whut.eval.infra.nacos.parser.ConfigPayloadParser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IndexOptionsConfigProviderTest {

    private final NacosTypedConfigConfiguration configuration = new NacosTypedConfigConfiguration();

    @Test
    void shouldRegisterIndexOptionsTypedBinding() {
        TypedConfigBindingRegistry bindingRegistry = configuration.typedConfigBindingRegistry();

        assertThat(bindingRegistry.find(IndexOptionsConfigProvider.DEFINITION_NAME)).isPresent();
        assertThat(bindingRegistry.find(IndexOptionsConfigProvider.DEFINITION_NAME).orElseThrow().targetType())
                .isEqualTo(IndexOptionsConfig.class);
    }

    @Test
    void shouldMaterializeIndexOptionsConfigFromYamlPayload() {
        TypedConfigBindingRegistry bindingRegistry = configuration.typedConfigBindingRegistry();
        TypedConfigRepository typedConfigRepository = configuration.typedConfigRepository();
        ConfigPayloadParser yamlParser = configuration.yamlConfigPayloadParser();
        TypedConfigMaterializer materializer = configuration.typedConfigMaterializer(
                bindingRegistry,
                typedConfigRepository,
                List.of(yamlParser)
        );
        IndexOptionsConfigProvider configProvider = new IndexOptionsConfigProvider(typedConfigRepository);

        materializer.materialize(
                IndexOptionsConfigProvider.DEFINITION_NAME,
                new RawConfigPayload(
                        ConfigResource.yaml("whut-eval-index-options.yaml"),
                        "index-options:\n"
                                + "  options-001:\n"
                                + "    - optionCode: OPT001\n"
                                + "      optionName: 选项1\n"
                                + "      points: 5.0\n"
                                + "      description: 选项描述\n"
                                + "      sortOrder: 1\n"
                                + "      allowCustomPoints: false\n",
                        "unit-test",
                        Instant.parse("2026-05-14T09:35:00Z")
                )
        );

        IndexOptionsConfig config = configProvider.requiredConfig();
        assertThat(config.getIndexOptions()).containsKey("options-001");
        List<IndexOptionsConfig.OptionItem> items = config.getIndexOptions().get("options-001");
        assertThat(items).hasSize(1);
        IndexOptionsConfig.OptionItem item = items.get(0);
        assertThat(item.getOptionCode()).isEqualTo("OPT001");
        assertThat(item.getOptionName()).isEqualTo("选项1");
        assertThat(item.getPoints()).isEqualTo(new BigDecimal("5.0"));
        assertThat(item.getDescription()).isEqualTo("选项描述");
        assertThat(item.getSortOrder()).isEqualTo(1);
        assertThat(item.isAllowCustomPoints()).isFalse();
    }

    @Test
    void shouldFailFastWhenIndexOptionsTypedConfigIsMissing() {
        TypedConfigRepository typedConfigRepository = configuration.typedConfigRepository();
        IndexOptionsConfigProvider configProvider = new IndexOptionsConfigProvider(typedConfigRepository);

        assertThat(configProvider.currentConfig()).isEmpty();
        assertThatThrownBy(configProvider::requiredConfig)
                .isInstanceOf(ConfigLoadException.class)
                .hasMessage("Required typed config not found: " + IndexOptionsConfigProvider.DEFINITION_NAME);
    }
}
