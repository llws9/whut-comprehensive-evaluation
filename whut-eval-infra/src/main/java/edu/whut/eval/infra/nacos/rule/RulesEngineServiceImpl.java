package edu.whut.eval.infra.nacos.rule;

import edu.whut.eval.common.exception.ConfigLoadException;
import edu.whut.eval.domain.config.RuleEngineService;
import edu.whut.eval.domain.config.StudentContext;
import edu.whut.eval.domain.config.StudentEvaluationSummary;
import edu.whut.eval.domain.config.repository.TypedConfigRepository;
import edu.whut.eval.domain.config.model.EligibilityRulesConfig;
import edu.whut.eval.domain.config.model.EvaluationItemsConfig;
import edu.whut.eval.domain.config.model.IndexOptionsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class RulesEngineServiceImpl implements RuleEngineService {

    private static final Logger log = LoggerFactory.getLogger(RulesEngineServiceImpl.class);
    private static final String EVALUATION_ITEMS_CONFIG = "evaluation-items-config";
    private static final String INDEX_OPTIONS_CONFIG = "index-options-config";
    private static final String ELIGIBILITY_RULES_CONFIG = "eligibility-rules-config";

    private final TypedConfigRepository configRepository;
    private final SpelExpressionParser spelParser;

    public RulesEngineServiceImpl(TypedConfigRepository configRepository) {
        this.configRepository = configRepository;
        this.spelParser = new SpelExpressionParser();
    }

    @Override
    public BigDecimal calculatePoints(String itemCode, String optionCode, StudentContext context) {
        if (optionCode == null || optionCode.isBlank()) {
            return BigDecimal.ZERO;
        }
        IndexOptionsConfig config = configRepository.find(INDEX_OPTIONS_CONFIG, IndexOptionsConfig.class)
                .orElseThrow(() -> new ConfigLoadException("index-options config not found"));

        String optionsKey = resolveOptionsKey(itemCode);
        List<IndexOptionsConfig.OptionItem> options = safeOptions(config)
                .getOrDefault(optionsKey, Collections.emptyList());

        IndexOptionsConfig.OptionItem matchedOption = options.stream()
                .filter(item -> item.getOptionCode().equals(optionCode))
                .filter(item -> evaluateCondition(item.getCondition(), context))
                .findFirst()
                .orElse(null);

        if (matchedOption == null) {
            return BigDecimal.ZERO;
        }

        if (matchedOption.isAllowCustomPoints() && matchedOption.getPoints() == null) {
            return null;
        }

        return matchedOption.getPoints() != null ? matchedOption.getPoints() : BigDecimal.ZERO;
    }

    @Override
    public BigDecimal calculateMaxPoints(String itemCode, StudentContext context) {
        EvaluationItemsConfig config = configRepository.find(EVALUATION_ITEMS_CONFIG, EvaluationItemsConfig.class)
                .orElseThrow(() -> new ConfigLoadException("evaluation-items config not found"));

        EvaluationItemsConfig.EvaluationItem item = findItemByCode(config, itemCode);

        if (item.getMaxPointsExpression() != null && !item.getMaxPointsExpression().isEmpty()) {
            return evaluateMaxPointsExpression(item.getMaxPointsExpression(), context);
        }

        return item.getMaxPoints();
    }

    @Override
    public boolean allowsCustomPoints(String itemCode, String optionCode) {
        if (optionCode == null || optionCode.isBlank()) {
            return false;
        }
        IndexOptionsConfig config = configRepository.find(INDEX_OPTIONS_CONFIG, IndexOptionsConfig.class)
                .orElseThrow(() -> new ConfigLoadException("index-options config not found"));

        String optionsKey = resolveOptionsKey(itemCode);
        List<IndexOptionsConfig.OptionItem> options = safeOptions(config)
                .getOrDefault(optionsKey, Collections.emptyList());

        return options.stream()
                .filter(item -> item.getOptionCode().equals(optionCode))
                .anyMatch(IndexOptionsConfig.OptionItem::isAllowCustomPoints);
    }

    @Override
    public boolean evaluateEligibility(String categoryCode, StudentEvaluationSummary summary) {
        EligibilityRulesConfig config = configRepository.find(ELIGIBILITY_RULES_CONFIG, EligibilityRulesConfig.class)
                .orElseThrow(() -> new ConfigLoadException("eligibility-rules config not found"));

        List<EligibilityRulesConfig.EligibilityRuleItem> rules = safeRules(config)
                .getOrDefault(categoryCode, Collections.emptyList());

        return rules.stream()
                .filter(EligibilityRulesConfig.EligibilityRuleItem::isEnabled)
                .allMatch(rule -> evaluateSpelExpression(rule.getExpression(), summary));
    }

    private EvaluationItemsConfig.EvaluationItem findItemByCode(EvaluationItemsConfig config, String itemCode) {
        return safeItems(config).values().stream()
                .flatMap(List::stream)
                .filter(item -> item.getItemCode().equals(itemCode))
                .findFirst()
                .orElseThrow(() -> new ConfigLoadException("Evaluation item not found: " + itemCode));
    }

    private String resolveOptionsKey(String itemCode) {
        EvaluationItemsConfig config = configRepository.find(EVALUATION_ITEMS_CONFIG, EvaluationItemsConfig.class)
                .orElseThrow(() -> new ConfigLoadException("evaluation-items config not found"));
        EvaluationItemsConfig.EvaluationItem item = findItemByCode(config, itemCode);
        if (item.getOptionsKey() != null && !item.getOptionsKey().isBlank()) {
            return item.getOptionsKey();
        }
        throw new ConfigLoadException("optionsKey not configured for evaluation item: " + itemCode);
    }

    private Map<String, List<EvaluationItemsConfig.EvaluationItem>> safeItems(EvaluationItemsConfig config) {
        return config.getEvaluationItems() == null ? Collections.emptyMap() : config.getEvaluationItems();
    }

    private Map<String, List<IndexOptionsConfig.OptionItem>> safeOptions(IndexOptionsConfig config) {
        return config.getIndexOptions() == null ? Collections.emptyMap() : config.getIndexOptions();
    }

    private Map<String, List<EligibilityRulesConfig.EligibilityRuleItem>> safeRules(EligibilityRulesConfig config) {
        return config.getEligibilityRules() == null ? Collections.emptyMap() : config.getEligibilityRules();
    }

    private boolean evaluateCondition(String condition, StudentContext context) {
        if (condition == null || condition.isEmpty() || "true".equals(condition)) {
            return true;
        }
        if (isUnsafeExpression(condition)) {
            log.warn("Blocked unsafe condition expression: {}", condition);
            return false;
        }
        try {
            Expression exp = spelParser.parseExpression(condition);
            SimpleEvaluationContext evalContext = createStudentEvaluationContext(context);
            Object result = exp.getValue(evalContext);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("Failed to evaluate condition: {}", condition, e);
            return false;
        }
    }

    private BigDecimal evaluateMaxPointsExpression(String expression, StudentContext context) {
        if (isUnsafeExpression(expression)) {
            log.warn("Blocked unsafe maxPoints expression: {}", expression);
            return null;
        }
        try {
            Expression exp = spelParser.parseExpression(expression);
            SimpleEvaluationContext evalContext = createStudentEvaluationContext(context);
            Object result = exp.getValue(evalContext);
            if (result instanceof Number) {
                return BigDecimal.valueOf(((Number) result).doubleValue());
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to evaluate maxPoints expression: {}", expression, e);
            return null;
        }
    }

    private boolean evaluateSpelExpression(String expression, StudentEvaluationSummary summary) {
        if (isUnsafeExpression(expression)) {
            log.warn("Blocked unsafe eligibility expression: {}", expression);
            return false;
        }
        try {
            Expression exp = spelParser.parseExpression(expression);
            SimpleEvaluationContext evalContext = createSummaryEvaluationContext(summary);
            Object result = exp.getValue(evalContext);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("Failed to evaluate eligibility expression: {}", expression, e);
            return false;
        }
    }

    private SimpleEvaluationContext createStudentEvaluationContext(StudentContext context) {
        return SimpleEvaluationContext.forReadOnlyDataBinding()
                .withRootObject(context)
                .build();
    }

    private SimpleEvaluationContext createSummaryEvaluationContext(StudentEvaluationSummary summary) {
        return SimpleEvaluationContext.forReadOnlyDataBinding()
                .withRootObject(summary)
                .build();
    }

    private boolean isUnsafeExpression(String expression) {
        if (expression == null || expression.isBlank()) {
            return false;
        }
        String normalized = expression.toLowerCase(Locale.ROOT);
        return normalized.contains("t(")
                || normalized.contains("class")
                || normalized.contains("new ")
                || normalized.contains("#this")
                || normalized.contains("#root")
                || normalized.contains("@")
                || normalized.contains("[")
                || normalized.contains("]");
    }
}
