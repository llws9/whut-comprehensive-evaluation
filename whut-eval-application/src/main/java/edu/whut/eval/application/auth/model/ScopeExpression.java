package edu.whut.eval.application.auth.model;

import java.util.Collections;
import java.util.List;

public class ScopeExpression {

    private final List<ScopeExpressionCondition> allOf;

    public ScopeExpression(List<ScopeExpressionCondition> allOf) {
        this.allOf = allOf == null ? Collections.emptyList() : List.copyOf(allOf);
    }

    public List<ScopeExpressionCondition> getAllOf() {
        return allOf;
    }

    public boolean isEmpty() {
        return allOf.isEmpty();
    }
}
