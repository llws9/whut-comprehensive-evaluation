package edu.whut.eval.domain.auth.model;

/**
 * 应用范围子句。
 * 表示申请查询可消费的谓词子句。
 */
public class ApplicationScopeClause {

    private final String scopeType;
    private final Long applicantUserId;
    private final Long orgUnitId;
    private final Long orgSubtreeRootId;
    private final String categoryCode;
    private final String itemCode;
    private final String expressionJson;

    public ApplicationScopeClause(String scopeType,
                                  Long applicantUserId,
                                  Long orgUnitId,
                                  Long orgSubtreeRootId,
                                  String categoryCode,
                                  String itemCode,
                                  String expressionJson) {
        this.scopeType = scopeType;
        this.applicantUserId = applicantUserId;
        this.orgUnitId = orgUnitId;
        this.orgSubtreeRootId = orgSubtreeRootId;
        this.categoryCode = categoryCode;
        this.itemCode = itemCode;
        this.expressionJson = expressionJson;
    }

    public String getScopeType() {
        return scopeType;
    }

    public Long getApplicantUserId() {
        return applicantUserId;
    }

    public Long getOrgUnitId() {
        return orgUnitId;
    }

    public Long getOrgSubtreeRootId() {
        return orgSubtreeRootId;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public String getItemCode() {
        return itemCode;
    }

    public String getExpressionJson() {
        return expressionJson;
    }
}
