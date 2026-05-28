package edu.whut.eval.application.auth.model;

/**
 * @deprecated 使用 {@link edu.whut.eval.domain.auth.model.AuthorizationScope} 代替。
 *             此类仅为向后兼容保留，将在未来版本中删除。
 */
@Deprecated
public class AuthorizationScope extends edu.whut.eval.domain.auth.model.AuthorizationScope {

    public AuthorizationScope(String permissionCode,
                              String scopeType,
                              Long orgUnitId,
                              String categoryCode,
                              String itemCode,
                              String expressionJson,
                              Integer priority) {
        super(permissionCode, scopeType, orgUnitId, categoryCode, itemCode, expressionJson, priority);
    }
}
