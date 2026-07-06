package edu.whut.eval.application.platform.service;

import edu.whut.eval.application.platform.query.EvaluationItemResponse;
import edu.whut.eval.application.platform.query.PlatformMenuDeadline;
import edu.whut.eval.application.platform.query.PlatformMenuStatus;
import edu.whut.eval.common.exception.ConfigLoadException;
import edu.whut.eval.domain.config.model.EvaluationItemsConfig;
import edu.whut.eval.domain.config.model.PlatformRuleConfig;
import edu.whut.eval.domain.config.repository.TypedConfigRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class PlatformReadApplicationService {

    private static final String PLATFORM_RULE_CONFIG = "platform-rule-config";
    private static final String EVALUATION_ITEMS_CONFIG = "evaluation-items-config";
    private static final String SOURCE_NACOS = "NACOS";

    private final TypedConfigRepository typedConfigRepository;

    public PlatformReadApplicationService(TypedConfigRepository typedConfigRepository) {
        this.typedConfigRepository = typedConfigRepository;
    }

    public PlatformMenuStatus getMenuStatus() {
        PlatformRuleConfig config = requiredPlatformRuleConfig();
        return new PlatformMenuStatus(
                config.isStudentApplyEnabled(),
                config.isFinalSubmitEnabled(),
                SOURCE_NACOS
        );
    }

    public PlatformMenuDeadline getMenuDeadline() {
        PlatformRuleConfig config = requiredPlatformRuleConfig();
        return new PlatformMenuDeadline(
                config.getStudentApplyDeadline(),
                config.getFinalSubmitDeadline(),
                SOURCE_NACOS
        );
    }

    public List<EvaluationItemResponse> listEvaluationItems(String categoryCode) {
        EvaluationItemsConfig config = typedConfigRepository.find(EVALUATION_ITEMS_CONFIG, EvaluationItemsConfig.class)
                .orElseThrow(() -> new ConfigLoadException("Required typed config not found: " + EVALUATION_ITEMS_CONFIG));

        return safeItems(config).values().stream()
                .flatMap(List::stream)
                .filter(EvaluationItemsConfig.EvaluationItem::isEnabled)
                .filter(item -> categoryCode == null || categoryCode.isBlank() || categoryCode.equals(item.getCategoryCode()))
                .sorted(Comparator.comparing(EvaluationItemsConfig.EvaluationItem::getCategoryCode, Comparator.nullsLast(String::compareTo))
                        .thenComparingInt(EvaluationItemsConfig.EvaluationItem::getSortOrder)
                        .thenComparing(EvaluationItemsConfig.EvaluationItem::getItemCode, Comparator.nullsLast(String::compareTo)))
                .map(this::toResponse)
                .toList();
    }

    private PlatformRuleConfig requiredPlatformRuleConfig() {
        return typedConfigRepository.find(PLATFORM_RULE_CONFIG, PlatformRuleConfig.class)
                .orElseThrow(() -> new ConfigLoadException("Required typed config not found: " + PLATFORM_RULE_CONFIG));
    }

    private Map<String, List<EvaluationItemsConfig.EvaluationItem>> safeItems(EvaluationItemsConfig config) {
        return config.getEvaluationItems() == null ? Map.of() : config.getEvaluationItems();
    }

    private EvaluationItemResponse toResponse(EvaluationItemsConfig.EvaluationItem item) {
        return new EvaluationItemResponse(
                item.getCategoryCode(),
                item.getCategoryName(),
                item.getItemCode(),
                item.getItemName(),
                item.getDescription(),
                item.getMaxPoints(),
                item.getMaxPointsExpression(),
                item.getApplyMode(),
                item.isEnabled(),
                item.getSortOrder(),
                item.getOptionsKey()
        );
    }
}
