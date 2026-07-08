package edu.whut.eval.domain.finalrecord.service;

import edu.whut.eval.domain.auth.model.ApplicationScopeClause;
import edu.whut.eval.domain.auth.model.ApplicationScopePredicate;
import edu.whut.eval.domain.auth.model.AuthorizationScope;
import edu.whut.eval.domain.auth.model.AuthorizationScopeSet;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FinalRecordScopePredicateBuilder {

    public ApplicationScopePredicate buildForFinalRecord(UserAuthorizationContext authorizationContext,
                                                        AuthorizationScopeSet scopeSet) {
        if (authorizationContext == null) {
            throw new IllegalArgumentException("authorizationContext must not be null");
        }
        if (scopeSet == null) {
            throw new IllegalArgumentException("scopeSet must not be null");
        }
        if (!scopeSet.isGranted()) {
            return ApplicationScopePredicate.denied(scopeSet.getPermissionCode());
        }
        if (scopeSet.allowsAll()) {
            return ApplicationScopePredicate.allowAll(scopeSet.getPermissionCode());
        }

        List<ApplicationScopeClause> clauses = new ArrayList<>();
        for (AuthorizationScope scope : scopeSet.getScopes()) {
            String scopeType = normalize(scope.getScopeType());
            if ("ORG_UNIT".equals(scopeType)) {
                clauses.add(new ApplicationScopeClause(scopeType, null, scope.getOrgUnitId(), null, null, null, null));
            } else if ("ORG_SUBTREE".equals(scopeType)) {
                clauses.add(new ApplicationScopeClause(scopeType, null, null, scope.getOrgUnitId(), null, null, null));
            }
        }
        return ApplicationScopePredicate.restricted(scopeSet.getPermissionCode(), clauses);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
