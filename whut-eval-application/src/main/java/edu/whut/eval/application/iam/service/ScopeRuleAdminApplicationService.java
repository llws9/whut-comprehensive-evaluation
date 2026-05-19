package edu.whut.eval.application.iam.service;

import edu.whut.eval.application.iam.command.CreateScopeRuleCommand;
import edu.whut.eval.application.iam.query.ScopeRuleAdminView;

import java.util.List;

/**
 * 管理端范围规则应用服务契约。
 */
public interface ScopeRuleAdminApplicationService {

    List<ScopeRuleAdminView> listScopeRules(Long assignmentId);

    ScopeRuleAdminView createScopeRule(Long assignmentId, CreateScopeRuleCommand command);
}
