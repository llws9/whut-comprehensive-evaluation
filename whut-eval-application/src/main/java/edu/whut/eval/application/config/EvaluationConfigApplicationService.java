package edu.whut.eval.application.config;

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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EvaluationConfigApplicationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationConfigApplicationService.class);
    private static final String EVALUATION_ITEMS_CONFIG = "evaluation-items-config";
    private static final String INDEX_OPTIONS_CONFIG = "index-options-config";
    private static final String ELIGIBILITY_RULES_CONFIG = "eligibility-rules-config";

    private final TypedConfigRepository configRepository;
    private final RuleEngineService ruleEngineService;

    public EvaluationConfigApplicationService(TypedConfigRepository configRepository,
                                              RuleEngineService ruleEngineService) {
        this.configRepository = configRepository;
        this.ruleEngineService = ruleEngineService;
    }

    public List<EvaluationItemsConfig.EvaluationItem> getItemsByCategory(String categoryCode) {
        EvaluationItemsConfig config = configRepository.find(EVALUATION_ITEMS_CONFIG, EvaluationItemsConfig.class)
                .orElseThrow(() -> new ConfigLoadException("evaluation-items config not found"));

        return safeItems(config).getOrDefault(categoryCode, Collections.emptyList())
                .stream()
                .filter(EvaluationItemsConfig.EvaluationItem::isEnabled)
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .collect(Collectors.toList());
    }

    public EvaluationItemsConfig.EvaluationItem getEvaluationItem(String itemCode) {
        EvaluationItemsConfig config = configRepository.find(EVALUATION_ITEMS_CONFIG, EvaluationItemsConfig.class)
                .orElseThrow(() -> new ConfigLoadException("evaluation-items config not found"));

        return safeItems(config).values().stream()
                .flatMap(List::stream)
                .filter(item -> item.getItemCode().equals(itemCode))
                .findFirst()
                .orElseThrow(() -> new ConfigLoadException("Evaluation item not found: " + itemCode));
    }

    public List<IndexOptionsConfig.OptionItem> getOptionsByItemCode(String itemCode) {
        EvaluationItemsConfig.EvaluationItem item = getEvaluationItem(itemCode);
        if (item.getOptionsKey() == null || item.getOptionsKey().isBlank()) {
            return Collections.emptyList();
        }
        String optionsKey = item.getOptionsKey();
        IndexOptionsConfig config = configRepository.find(INDEX_OPTIONS_CONFIG, IndexOptionsConfig.class)
                .orElseThrow(() -> new ConfigLoadException("index-options config not found"));

        List<IndexOptionsConfig.OptionItem> options = safeOptions(config)
                .getOrDefault(optionsKey, Collections.emptyList());

        return options.stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .collect(Collectors.toList());
    }

    public BigDecimal calculatePoints(String itemCode, String optionCode, StudentContext context) {
        return ruleEngineService.calculatePoints(itemCode, optionCode, context);
    }

    public BigDecimal calculateMaxPoints(String itemCode, StudentContext context) {
        return ruleEngineService.calculateMaxPoints(itemCode, context);
    }

    public boolean allowsCustomPoints(String itemCode, String optionCode) {
        return ruleEngineService.allowsCustomPoints(itemCode, optionCode);
    }

    public boolean evaluateEligibility(String categoryCode, StudentEvaluationSummary summary) {
        return ruleEngineService.evaluateEligibility(categoryCode, summary);
    }

    public Map<String, List<EligibilityRulesConfig.EligibilityRuleItem>> getAllEligibilityRules() {
        EligibilityRulesConfig config = configRepository.find(ELIGIBILITY_RULES_CONFIG, EligibilityRulesConfig.class)
                .orElseThrow(() -> new ConfigLoadException("eligibility-rules config not found"));
        return safeRules(config);
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
}
