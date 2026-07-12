package edu.whut.eval.infra.security.sql;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class SqlPredicateFragment {

    private final String expression;
    private final Map<String, Object> parameters;

    public SqlPredicateFragment(String expression, Map<String, Object> parameters) {
        this.expression = expression == null ? "" : expression;
        this.parameters = parameters == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }

    public static SqlPredicateFragment allowAll() {
        return new SqlPredicateFragment("", Map.of());
    }

    public static SqlPredicateFragment alwaysTrue() {
        return new SqlPredicateFragment("1 = 1", Map.of());
    }

    public static SqlPredicateFragment denyAll() {
        return new SqlPredicateFragment("1 = 0", Map.of());
    }

    public String getExpression() {
        return expression;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public boolean isAllowAll() {
        return expression.isBlank();
    }
}
