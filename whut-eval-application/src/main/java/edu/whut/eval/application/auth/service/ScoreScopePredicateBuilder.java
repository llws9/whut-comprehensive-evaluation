package edu.whut.eval.application.auth.service;

import edu.whut.eval.domain.auth.model.AuthorizationScopeSet;
import edu.whut.eval.domain.auth.model.ScoreScopePredicate;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;

/**
 * 把抽象范围集合转换成成绩查询可消费的谓词对象。
 */
public interface ScoreScopePredicateBuilder {

    /**
     * 将一个权限码下的范围集合翻译成成绩查询子句集合。
     */
    ScoreScopePredicate build(UserAuthorizationContext authorizationContext,
                              AuthorizationScopeSet scopeSet);
}
