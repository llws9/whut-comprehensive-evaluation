package edu.whut.eval.application.auth.model;

import java.util.List;

/**
 * @deprecated 使用 {@link edu.whut.eval.domain.auth.model.ScopeExpression} 代替。
 *             此类仅为向后兼容保留，将在未来版本中删除。
 */
@Deprecated
public class ScopeExpression extends edu.whut.eval.domain.auth.model.ScopeExpression {

    public ScopeExpression(List<edu.whut.eval.domain.auth.model.ScopeExpressionCondition> allOf) {
        super(allOf);
    }
}
