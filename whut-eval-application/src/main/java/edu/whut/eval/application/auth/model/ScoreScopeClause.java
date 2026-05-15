package edu.whut.eval.application.auth.model;

public class ScoreScopeClause {

    private final String scopeType;
    private final Long studentUserId;
    private final Long orgUnitId;
    private final Long orgSubtreeRootId;
    private final String categoryCode;
    private final String itemCode;
    private final String academicYear;
    private final String expressionJson;

    public ScoreScopeClause(String scopeType,
                            Long studentUserId,
                            Long orgUnitId,
                            Long orgSubtreeRootId,
                            String categoryCode,
                            String itemCode,
                            String academicYear,
                            String expressionJson) {
        this.scopeType = scopeType;
        this.studentUserId = studentUserId;
        this.orgUnitId = orgUnitId;
        this.orgSubtreeRootId = orgSubtreeRootId;
        this.categoryCode = categoryCode;
        this.itemCode = itemCode;
        this.academicYear = academicYear;
        this.expressionJson = expressionJson;
    }

    public String getScopeType() {
        return scopeType;
    }

    public Long getStudentUserId() {
        return studentUserId;
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

    public String getAcademicYear() {
        return academicYear;
    }

    public String getExpressionJson() {
        return expressionJson;
    }
}
