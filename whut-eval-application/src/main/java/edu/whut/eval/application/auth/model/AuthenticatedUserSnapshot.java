package edu.whut.eval.application.auth.model;

import edu.whut.eval.domain.iam.model.IamScopeRule;

import java.util.List;
import java.util.Set;

public record AuthenticatedUserSnapshot(
        Long userId,
        String userNo,
        String userName,
        String identity,
        Set<String> roles,
        Set<String> authorities,
        List<IamScopeRule> scopeRules
) {
}
