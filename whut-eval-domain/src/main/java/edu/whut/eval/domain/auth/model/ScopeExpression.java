package edu.whut.eval.domain.auth.model;

import java.util.Collections;
import java.util.List;

/**
 * 范围表达式。
 * 表示一个由多个条件组成的范围表达式。
 */
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
