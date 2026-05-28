package edu.whut.eval.infra.nacos.config;

import edu.whut.eval.common.exception.ConfigLoadException;
import edu.whut.eval.domain.config.repository.TypedConfigRepository;
import edu.whut.eval.domain.config.model.EvaluationItemsConfig;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Evaluation items typed config 读取门面。
 * 通过该类统一读取测评项目配置，避免业务层直接依赖 definition name 字符串。
 */
@Component
public class EvaluationItemsConfigProvider {

    public static final String DEFINITION_NAME = "evaluation-items-config";

    private final TypedConfigRepository typedConfigRepository;

    public EvaluationItemsConfigProvider(TypedConfigRepository typedConfigRepository) {
        this.typedConfigRepository = typedConfigRepository;
    }

    /**
     * 尝试读取当前已 materialize 的测评项目配置；若启动阶段未加载成功则返回空。
     */
    public Optional<EvaluationItemsConfig> currentConfig() {
        return typedConfigRepository.find(DEFINITION_NAME, EvaluationItemsConfig.class);
    }

    /**
     * 强制要求当前已存在测评项目配置，否则抛出明确的配置加载异常。
     */
    public EvaluationItemsConfig requiredConfig() {
        return currentConfig().orElseThrow(() -> new ConfigLoadException(
                "Required typed config not found: " + DEFINITION_NAME
        ));
    }
}
