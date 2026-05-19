package edu.whut.eval.application.iam.service;

import edu.whut.eval.application.iam.command.CreateScopeRuleCommand;
import edu.whut.eval.application.iam.query.ScopeRuleAdminView;
import java.util.List;

/**
 * 管理端范围规则应用服务草稿实现。
 * 仅保留为历史草稿参考，不再注册为 Spring Bean。
 */
public class DraftScopeRuleAdminApplicationService implements ScopeRuleAdminApplicationService {

    @Override
    public List<ScopeRuleAdminView> listScopeRules(Long assignmentId) {
        throw new UnsupportedOperationException("TODO: implement list scope rules flow");
    }

    @Override
    public ScopeRuleAdminView createScopeRule(Long assignmentId, CreateScopeRuleCommand command) {
        throw new UnsupportedOperationException("TODO: implement create scope rule flow");
    }
}
