package edu.whut.eval.application.auth.service;

import edu.whut.eval.application.auth.model.ApplicationResourceContext;
import edu.whut.eval.application.auth.model.ScopeAccessDecision;
import edu.whut.eval.application.auth.model.ScoreResourceContext;
import edu.whut.eval.application.auth.model.UserAuthorizationContext;

/**
 * 对单条资源做范围命中判定，适用于详情查看、审批等非列表场景。
 */
public interface ResourceScopeAccessEvaluator {

    /**
     * 判断当前用户是否可访问某条申请资源。
     */
    ScopeAccessDecision canAccessApplication(UserAuthorizationContext authorizationContext,
                                             String permissionCode,
                                             ApplicationResourceContext resourceContext);

    /**
     * 判断当前用户是否可访问某条成绩资源。
     */
    ScopeAccessDecision canAccessScore(UserAuthorizationContext authorizationContext,
                                       String permissionCode,
                                       ScoreResourceContext resourceContext);
}
