package edu.whut.eval.application.auth.model;

import java.util.List;

/**
 * @deprecated 使用 {@link edu.whut.eval.domain.auth.model.ApplicationScopePredicate} 代替。
 *             此类仅为向后兼容保留，将在未来版本中删除。
 */
@Deprecated
public class ApplicationScopePredicate extends edu.whut.eval.domain.auth.model.ApplicationScopePredicate {

    public ApplicationScopePredicate(String permissionCode,
                                     boolean granted,
                                     boolean allowAll,
                                     List<edu.whut.eval.domain.auth.model.ApplicationScopeClause> clauses) {
        super(permissionCode, granted, allowAll, clauses);
    }
}
