package edu.whut.eval.application.auth.model;

/**
 * @deprecated 使用 {@link edu.whut.eval.domain.auth.model.ApplicationScopeClause} 代替。
 *             此类仅为向后兼容保留，将在未来版本中删除。
 */
@Deprecated
public class ApplicationScopeClause extends edu.whut.eval.domain.auth.model.ApplicationScopeClause {

    public ApplicationScopeClause(String scopeType,
                                  Long applicantUserId,
                                  Long orgUnitId,
                                  Long orgSubtreeRootId,
                                  String categoryCode,
                                  String itemCode,
                                  String expressionJson) {
        super(scopeType, applicantUserId, orgUnitId, orgSubtreeRootId, categoryCode, itemCode, expressionJson);
    }
}
