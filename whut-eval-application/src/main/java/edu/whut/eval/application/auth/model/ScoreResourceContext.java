package edu.whut.eval.application.auth.model;

public class ScoreResourceContext implements ScopeResourceContext {

    private final Long scoreId;
    private final Long studentUserId;
    private final Long orgUnitId;
    private final String orgPath;
    private final String categoryCode;
    private final String itemCode;
    private final String academicYear;

    public ScoreResourceContext(Long scoreId,
                                Long studentUserId,
                                Long orgUnitId,
                                String orgPath,
                                String categoryCode,
                                String itemCode,
                                String academicYear) {
        this.scoreId = scoreId;
        this.studentUserId = studentUserId;
        this.orgUnitId = orgUnitId;
        this.orgPath = orgPath;
        this.categoryCode = categoryCode;
        this.itemCode = itemCode;
        this.academicYear = academicYear;
    }

    public Long getScoreId() {
        return scoreId;
    }

    public Long getStudentUserId() {
        return studentUserId;
    }

    @Override
    public Long getOwnerUserId() {
        return studentUserId;
    }

    @Override
    public Long getOrgUnitId() {
        return orgUnitId;
    }

    @Override
    public String getOrgPath() {
        return orgPath;
    }

    @Override
    public String getCategoryCode() {
        return categoryCode;
    }

    @Override
    public String getItemCode() {
        return itemCode;
    }

    public String getAcademicYear() {
        return academicYear;
    }

    @Override
    public Object getFieldValue(String fieldName) {
        if ("scoreId".equals(fieldName)) {
            return scoreId;
        }
        if ("studentUserId".equals(fieldName) || "ownerUserId".equals(fieldName)) {
            return studentUserId;
        }
        if ("orgUnitId".equals(fieldName)) {
            return orgUnitId;
        }
        if ("orgPath".equals(fieldName)) {
            return orgPath;
        }
        if ("categoryCode".equals(fieldName)) {
            return categoryCode;
        }
        if ("itemCode".equals(fieldName)) {
            return itemCode;
        }
        if ("academicYear".equals(fieldName)) {
            return academicYear;
        }
        return null;
    }
}
