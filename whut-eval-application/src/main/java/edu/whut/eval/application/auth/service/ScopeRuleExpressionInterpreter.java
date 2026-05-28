package edu.whut.eval.application.auth.service;

import edu.whut.eval.domain.auth.model.ScopeExpression;
import edu.whut.eval.application.auth.model.ScopeResourceContext;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;

/**
 * 解析并执行范围规则里的受控表达式。
 */
public interface ScopeRuleExpressionInterpreter {

    /**
     * 把持久化的表达式 JSON 解析成统一领域模型。
     */
    ScopeExpression parse(String expressionJson);

    /**
     * 结合当前用户与资源上下文判断表达式是否命中。
     */
    boolean matches(UserAuthorizationContext authorizationContext,
                    String expressionJson,
                    ScopeResourceContext resourceContext);
}
