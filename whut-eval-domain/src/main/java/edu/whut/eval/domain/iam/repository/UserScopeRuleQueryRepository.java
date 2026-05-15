package edu.whut.eval.domain.iam.repository;

import edu.whut.eval.domain.iam.model.IamScopeRule;

import java.util.List;

public interface UserScopeRuleQueryRepository {

    List<IamScopeRule> findActiveScopeRulesByUserId(Long userId);
}
