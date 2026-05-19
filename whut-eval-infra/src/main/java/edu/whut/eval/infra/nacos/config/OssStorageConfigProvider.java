package edu.whut.eval.infra.nacos.config;

import edu.whut.eval.common.exception.ConfigLoadException;
import edu.whut.eval.domain.config.repository.TypedConfigRepository;
import edu.whut.eval.infra.nacos.model.typed.OssStorageConfig;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * OSS typed config 读取门面。
 * 通过该类统一读取对象存储配置，避免业务层直接依赖 definition name 字符串。
 */
@Component
public class OssStorageConfigProvider {

    public static final String DEFINITION_NAME = "oss-storage-config";

    private final TypedConfigRepository typedConfigRepository;

    public OssStorageConfigProvider(TypedConfigRepository typedConfigRepository) {
        this.typedConfigRepository = typedConfigRepository;
    }

    /**
     * 尝试读取当前已 materialize 的 OSS 配置；若启动阶段未加载成功则返回空。
     */
    public Optional<OssStorageConfig> currentConfig() {
        return typedConfigRepository.find(DEFINITION_NAME, OssStorageConfig.class);
    }

    /**
     * 强制要求当前已存在 OSS 配置，否则抛出明确的配置加载异常。
     */
    public OssStorageConfig requiredConfig() {
        return currentConfig().orElseThrow(() -> new ConfigLoadException(
                "Required typed config not found: " + DEFINITION_NAME
        ));
    }
}
