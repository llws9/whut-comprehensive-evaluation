package edu.whut.eval.application.auth.model;

import java.util.List;

/**
 * @deprecated 使用 {@link edu.whut.eval.domain.auth.model.ScopeExpressionCondition} 代替。
 *             此类仅为向后兼容保留，将在未来版本中删除。
 */
@Deprecated
public class ScopeExpressionCondition extends edu.whut.eval.domain.auth.model.ScopeExpressionCondition {

    public ScopeExpressionCondition(String field,
                                    String operator,
                                    Object value,
                                    List<Object> values,
                                    String valueFrom) {
        super(field, operator, value, values, valueFrom);
    }
}
