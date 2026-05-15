package edu.whut.eval.application.auth.service;

import edu.whut.eval.application.auth.model.ApplicationScopePredicate;
import edu.whut.eval.application.auth.model.AuthorizationScopeSet;
import edu.whut.eval.application.auth.model.UserAuthorizationContext;

/**
 * 把抽象范围集合转换成申请查询可消费的谓词对象。
 */
public interface ScopePredicateBuilder {

    /**
     * 将一个权限码下的范围集合翻译成申请查询子句集合。
     */
    ApplicationScopePredicate buildForApplication(UserAuthorizationContext authorizationContext,
                                                  AuthorizationScopeSet scopeSet);
}
