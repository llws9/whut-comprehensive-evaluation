package edu.whut.eval.application.auth.model;

/**
 * @deprecated 使用 {@link edu.whut.eval.domain.auth.model.ScoreScopeClause} 代替。
 *             此类仅为向后兼容保留，将在未来版本中删除。
 */
@Deprecated
public class ScoreScopeClause extends edu.whut.eval.domain.auth.model.ScoreScopeClause {

    public ScoreScopeClause(String scopeType,
                            Long studentUserId,
                            Long orgUnitId,
                            Long orgSubtreeRootId,
                            String categoryCode,
                            String itemCode,
                            String academicYear,
                            String expressionJson) {
        super(scopeType, studentUserId, orgUnitId, orgSubtreeRootId, categoryCode, itemCode, academicYear, expressionJson);
    }
}
