package edu.whut.eval.domain.config;

import java.math.BigDecimal;

public interface RuleEngineService {

    BigDecimal calculatePoints(String itemCode, String optionCode, StudentContext context);

    BigDecimal calculateMaxPoints(String itemCode, StudentContext context);

    boolean allowsCustomPoints(String itemCode, String optionCode);

    boolean evaluateEligibility(String categoryCode, StudentEvaluationSummary summary);
}