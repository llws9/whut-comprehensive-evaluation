package edu.whut.eval.infra.nacos.config;

import edu.whut.eval.common.exception.ConfigLoadException;
import edu.whut.eval.domain.config.repository.TypedConfigRepository;
import edu.whut.eval.domain.config.model.EligibilityRulesConfig;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Eligibility rules typed config 读取门面。
 * 通过该类统一读取资格规则配置,避免业务层直接依赖 definition name 字符串。
 */
@Component
public class EligibilityRulesConfigProvider {

    public static final String DEFINITION_NAME = "eligibility-rules-config";

    private final TypedConfigRepository typedConfigRepository;

    public EligibilityRulesConfigProvider(TypedConfigRepository typedConfigRepository) {
        this.typedConfigRepository = typedConfigRepository;
    }

    /**
     * 尝试读取当前已 materialize 的资格规则配置；若启动阶段未加载成功则返回空。
     */
    public Optional<EligibilityRulesConfig> currentConfig() {
        return typedConfigRepository.find(DEFINITION_NAME, EligibilityRulesConfig.class);
    }

    /**
     * 强制要求当前已存在资格规则配置,否则抛出明确的配置加载异常。
     */
    public EligibilityRulesConfig requiredConfig() {
        return currentConfig().orElseThrow(() -> new ConfigLoadException(
                "Required typed config not found: " + DEFINITION_NAME
        ));
    }
}
