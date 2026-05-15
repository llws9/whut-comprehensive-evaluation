package edu.whut.eval.domain.score.query;

/**
 * 正式成绩列表查询对象。
 * 业务过滤条件与授权范围条件在 Repository 中以 AND 方式组合。
 */
public class ScorePageQuery {

    private final long pageNo;
    private final long pageSize;
    private final Long scoreId;
    private final Long studentUserId;
    private final Long orgUnitId;
    private final String categoryCode;
    private final String itemCode;
    private final String academicYear;

    public ScorePageQuery(long pageNo,
                          long pageSize,
                          Long scoreId,
                          Long studentUserId,
                          Long orgUnitId,
                          String categoryCode,
                          String itemCode,
                          String academicYear) {
        if (pageNo < 1) {
            throw new IllegalArgumentException("pageNo must be greater than 0");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be greater than 0");
        }
        this.pageNo = pageNo;
        this.pageSize = pageSize;
        this.scoreId = scoreId;
        this.studentUserId = studentUserId;
        this.orgUnitId = orgUnitId;
        this.categoryCode = categoryCode;
        this.itemCode = itemCode;
        this.academicYear = academicYear;
    }

    public long getPageNo() {
        return pageNo;
    }

    public long getPageSize() {
        return pageSize;
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

    public String getCategoryCode() {
        return categoryCode;
    }

    public String getItemCode() {
        return itemCode;
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public long getOffset() {
        return (pageNo - 1) * pageSize;
    }
}
