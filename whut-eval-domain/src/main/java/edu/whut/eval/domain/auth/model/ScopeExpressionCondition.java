package edu.whut.eval.domain.auth.model;

import java.util.Collections;
import java.util.List;

/**
 * 范围表达式条件。
 * 表示范围表达式中的单个条件。
 */
public class ScopeExpressionCondition {

    private final String field;
    private final String operator;
    private final Object value;
    private final List<Object> values;
    private final String valueFrom;

    public ScopeExpressionCondition(String field,
                                    String operator,
                                    Object value,
                                    List<Object> values,
                                    String valueFrom) {
        this.field = field;
        this.operator = operator;
        this.value = value;
        this.values = values == null ? Collections.emptyList() : List.copyOf(values);
        this.valueFrom = valueFrom;
    }

    public String getField() {
        return field;
    }

    public String getOperator() {
        return operator;
    }

    public Object getValue() {
        return value;
    }

    public List<Object> getValues() {
        return values;
    }

    public String getValueFrom() {
        return valueFrom;
    }
}
