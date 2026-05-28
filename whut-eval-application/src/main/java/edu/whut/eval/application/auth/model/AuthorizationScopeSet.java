package edu.whut.eval.application.auth.model;

import java.util.List;

/**
 * @deprecated 使用 {@link edu.whut.eval.domain.auth.model.AuthorizationScopeSet} 代替。
 *             此类仅为向后兼容保留，将在未来版本中删除。
 */
@Deprecated
public class AuthorizationScopeSet extends edu.whut.eval.domain.auth.model.AuthorizationScopeSet {

    public AuthorizationScopeSet(String permissionCode, boolean granted, List<edu.whut.eval.domain.auth.model.AuthorizationScope> scopes) {
        super(permissionCode, granted, scopes);
    }
}
