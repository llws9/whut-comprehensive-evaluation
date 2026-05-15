package edu.whut.eval.application.score.query;

/**
 * 成绩列表对外查询视图。
 */
public class ScoreRecordView {

    private final Long scoreId;
    private final Long studentUserId;
    private final Long orgUnitId;
    private final String orgPath;
    private final String categoryCode;
    private final String itemCode;
    private final String academicYear;

    public ScoreRecordView(Long scoreId,
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

    public Long getOrgUnitId() {
        return orgUnitId;
    }

    public String getOrgPath() {
        return orgPath;
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
}
