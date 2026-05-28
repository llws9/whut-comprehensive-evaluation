package edu.whut.eval.app.config;

import edu.whut.eval.common.exception.ConfigLoadException;
import edu.whut.eval.infra.nacos.TypedConfigBindingRegistry;
import edu.whut.eval.infra.nacos.TypedConfigMaterializer;
import edu.whut.eval.domain.config.repository.TypedConfigRepository;
import edu.whut.eval.infra.nacos.config.NacosTypedConfigConfiguration;
import edu.whut.eval.infra.nacos.config.OssStorageConfigProvider;
import edu.whut.eval.infra.nacos.model.ConfigResource;
import edu.whut.eval.infra.nacos.model.RawConfigPayload;
import edu.whut.eval.domain.config.model.OssStorageConfig;
import edu.whut.eval.infra.nacos.parser.ConfigPayloadParser;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OssStorageTypedConfigConfigurationTest {

    private final NacosTypedConfigConfiguration configuration = new NacosTypedConfigConfiguration();

    @Test
    void shouldRegisterOssStorageTypedBinding() {
        TypedConfigBindingRegistry bindingRegistry = configuration.typedConfigBindingRegistry();

        assertThat(bindingRegistry.find(OssStorageConfigProvider.DEFINITION_NAME)).isPresent();
        assertThat(bindingRegistry.find(OssStorageConfigProvider.DEFINITION_NAME).orElseThrow().targetType())
                .isEqualTo(OssStorageConfig.class);
    }

    @Test
    void shouldMaterializeOssStorageConfigFromYamlPayload() {
        TypedConfigBindingRegistry bindingRegistry = configuration.typedConfigBindingRegistry();
        TypedConfigRepository typedConfigRepository = configuration.typedConfigRepository();
        ConfigPayloadParser yamlParser = configuration.yamlConfigPayloadParser();
        TypedConfigMaterializer materializer = configuration.typedConfigMaterializer(
                bindingRegistry,
                typedConfigRepository,
                List.of(yamlParser)
        );
        OssStorageConfigProvider configProvider = new OssStorageConfigProvider(typedConfigRepository);

        materializer.materialize(
                OssStorageConfigProvider.DEFINITION_NAME,
                new RawConfigPayload(
                        ConfigResource.yaml("whut-eval-oss-storage.yaml"),
                        "enabled: true\n"
                                + "endpoint: https://oss-cn-shanghai.aliyuncs.com\n"
                                + "region: cn-shanghai\n"
                                + "accessKeyId: test-ak\n"
                                + "accessKeySecret: test-sk\n"
                                + "bucket: whut-eval-dev\n"
                                + "publicBaseUrl: https://cdn.whut.example.com\n"
                                + "keyPrefix: uploads/dev\n",
                        "unit-test",
                        Instant.parse("2026-05-14T09:35:00Z")
                )
        );

        OssStorageConfig ossStorageConfig = configProvider.requiredConfig();
        assertThat(ossStorageConfig.isEnabled()).isTrue();
        assertThat(ossStorageConfig.getEndpoint()).isEqualTo("https://oss-cn-shanghai.aliyuncs.com");
        assertThat(ossStorageConfig.getRegion()).isEqualTo("cn-shanghai");
        assertThat(ossStorageConfig.getAccessKeyId()).isEqualTo("test-ak");
        assertThat(ossStorageConfig.getAccessKeySecret()).isEqualTo("test-sk");
        assertThat(ossStorageConfig.getBucket()).isEqualTo("whut-eval-dev");
        assertThat(ossStorageConfig.getPublicBaseUrl()).isEqualTo("https://cdn.whut.example.com");
        assertThat(ossStorageConfig.getKeyPrefix()).isEqualTo("uploads/dev");
    }

    @Test
    void shouldFailFastWhenOssTypedConfigIsMissing() {
        TypedConfigRepository typedConfigRepository = configuration.typedConfigRepository();
        OssStorageConfigProvider configProvider = new OssStorageConfigProvider(typedConfigRepository);

        assertThat(configProvider.currentConfig()).isEmpty();
        assertThatThrownBy(configProvider::requiredConfig)
                .isInstanceOf(ConfigLoadException.class)
                .hasMessage("Required typed config not found: " + OssStorageConfigProvider.DEFINITION_NAME);
    }
}
