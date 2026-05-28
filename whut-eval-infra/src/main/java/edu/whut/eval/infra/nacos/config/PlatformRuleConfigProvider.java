package edu.whut.eval.infra.nacos.config;

import edu.whut.eval.common.exception.ConfigLoadException;
import edu.whut.eval.domain.config.repository.TypedConfigRepository;
import edu.whut.eval.infra.nacos.model.typed.PlatformRuleConfig;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Platform rule typed config 读取门面。
 * 通过该类统一读取平台规则配置，避免业务层直接依赖 definition name 字符串。
 */
@Component
public class PlatformRuleConfigProvider {

    public static final String DEFINITION_NAME = "platform-rule-config";

    private final TypedConfigRepository typedConfigRepository;

    public PlatformRuleConfigProvider(TypedConfigRepository typedConfigRepository) {
        this.typedConfigRepository = typedConfigRepository;
    }

    /**
     * 尝试读取当前已 materialize 的平台规则配置；若启动阶段未加载成功则返回空。
     */
    public Optional<PlatformRuleConfig> currentConfig() {
        return typedConfigRepository.find(DEFINITION_NAME, PlatformRuleConfig.class);
    }

    /**
     * 强制要求当前已存在平台规则配置，否则抛出明确的配置加载异常。
     */
    public PlatformRuleConfig requiredConfig() {
        return currentConfig().orElseThrow(() -> new ConfigLoadException(
                "Required typed config not found: " + DEFINITION_NAME
        ));
    }
}
